package no.nav.sokos.ske.krav.service.integration

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.http.HttpStatusCode
import io.ktor.server.config.ApplicationConfig
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.spyk
import io.mockk.unmockkObject

import no.nav.sokos.ske.krav.client.SkeClient
import no.nav.sokos.ske.krav.client.SlackClient
import no.nav.sokos.ske.krav.client.SlackService
import no.nav.sokos.ske.krav.config.CircuitBreakerManager
import no.nav.sokos.ske.krav.config.PropertiesConfig
import no.nav.sokos.ske.krav.domain.Status
import no.nav.sokos.ske.krav.listener.DBListener
import no.nav.sokos.ske.krav.repository.FeilmeldingRepository
import no.nav.sokos.ske.krav.security.MaskinportenAccessTokenProvider
import no.nav.sokos.ske.krav.service.DatabaseService
import no.nav.sokos.ske.krav.service.StatusService
import no.nav.sokos.ske.krav.util.DBUtils.asyncTransaction
import no.nav.sokos.ske.krav.util.MockHttpClient
import no.nav.sokos.ske.krav.util.MockHttpClientUtils
import no.nav.sokos.ske.krav.util.getAllKrav
import no.nav.sokos.ske.krav.util.setUpMockHttpClient

internal class StatusServiceIntegrationTest :
    BehaviorSpec({
        extensions(DBListener)
        beforeEach { CircuitBreakerManager.circuitBreaker.reset() }
        val dbService by lazy { DatabaseService(DBListener.dataSource) }

        fun setupServices(
            client: HttpClient,
            databaseService: DatabaseService,
        ): Triple<SlackClient, SlackService, StatusService> {
            val slackClientSpy = spyk(SlackClient(client = MockHttpClient().getSlackClient()))
            val slackServiceSpy = spyk(SlackService(slackClientSpy), recordPrivateCalls = true)
            val skeClient = SkeClient(skeEndpoint = "", client = client, tokenProvider = mockk<MaskinportenAccessTokenProvider>(relaxed = true))
            val statusServiceSpy = spyk(StatusService(DBListener.dataSource, skeClient, databaseService, slackServiceSpy), recordPrivateCalls = true)

            return Triple(slackClientSpy, slackServiceSpy, statusServiceSpy)
        }

        beforeSpec {
            mockkObject(PropertiesConfig)
            every { PropertiesConfig.config } returns ApplicationConfig("application-test.conf")
        }

        Given("Mottaksstatus trigger circuit breaker") {
            DBListener.clearDB()
            DBListener.loadInitScript("SQLscript/KravSomSkalOppdateres.sql")
            val avskrivKravKall = MockHttpClientUtils.MockRequestObj(MockHttpClientUtils.Responses.genericFeilResponse(), MockHttpClientUtils.EndepunktType.MOTTAKSSTATUS, HttpStatusCode.Forbidden)

            val httpClient =
                setUpMockHttpClient(listOf(avskrivKravKall))
            val skeClient = SkeClient(skeEndpoint = "", client = httpClient, tokenProvider = mockk<MaskinportenAccessTokenProvider>(relaxed = true))

            dbService.getAllKravForStatusCheck().size shouldBe 5

            StatusService(DBListener.dataSource, skeClient, dbService, mockk<SlackService>(relaxed = true)).getMottaksStatus()
            Then("Skal krav ikke oppdateres") {
                dbService.getAllKravForStatusCheck().size shouldBe 5
            }
            CircuitBreakerManager.circuitBreaker.reset()
        }

        Given("Mottaksstatus er RESKONTROFOERT") {
            val mottaksStatusResponse = MockHttpClientUtils.Responses.mottaksStatusResponse(status = Status.RESKONTROFOERT.value)
            val httpClient = mottaksStatusMockHttpClient(mottaksStatusResponse)
            val (slackClientSpy, _, statusService) = setupServices(httpClient, dbService)

            Then("Skal mottaksstatus settes til RESKONTROFOERT i database") {
                val allKravBeforeUpdate = DBListener.dataSource.connection.use { con -> con.getAllKrav() }
                allKravBeforeUpdate.count { it.status == Status.RESKONTROFOERT.value } shouldBe 3

                statusService.getMottaksStatus()

                val allKravAfterUpdate = DBListener.dataSource.connection.use { con -> con.getAllKrav() }
                allKravAfterUpdate.count { it.status == Status.RESKONTROFOERT.value } shouldBe 8
            }
            Then("Alert skal ikke sendes") {
                coVerify(exactly = 0) {
                    slackClientSpy.sendMessage(any<String>(), any<String>(), any<Map<String, List<String>>>())
                }
            }
        }
        Given("Mottaksstatus er VALIDERINGSFEIL") {
            DBListener.clearDB()
            val fileName = "KravSomSkalOppdateres.sql"
            DBListener.loadInitScript("SQLscript/$fileName")
            val status = "ORGANISASJONSNUMMER_FINNES_IKKE"
            val mottaksStatusResponse = MockHttpClientUtils.Responses.mottaksStatusResponse(status = Status.VALIDERINGSFEIL_MOTTAKSSTATUS.value)
            val valideringsFeilRespons = MockHttpClientUtils.Responses.valideringsfeilResponse(status, "Organisasjon med organisasjonsnummer=xxxxxxxxx finnes ikke")
            val httpClient = mottaksStatusMockHttpClient(mottaksStatusResponse, valideringsFeilRespons)

            val (slackClientSpy, slackServiceSpy, statusService) = setupServices(httpClient, dbService)

            DBListener.dataSource.asyncTransaction { tx ->

                FeilmeldingRepository.getAllFeilmeldinger(tx).size shouldBe 0
                dbService.getAllKravForStatusCheck().size shouldBe 5

                statusService.getMottaksStatus()

                Then("Skal feilmelding lagres i Feilmelding tabell") {
                    val feilmeldinger = FeilmeldingRepository.getAllFeilmeldinger(tx)
                    feilmeldinger.size shouldBe 5
                    feilmeldinger.forEach {
                        it.error shouldBe status
                        it.melding shouldBe "Organisasjon med organisasjonsnummer=xxxxxxxxx finnes ikke"
                    }
                }

                Then("Mottaksstatus skal settes til VALIDERINGSFEIL i database") {
                    DBListener.dataSource.connection
                        .use { con -> con.getAllKrav() }
                        .filter { it.status == Status.VALIDERINGSFEIL_MOTTAKSSTATUS.value }
                        .distinctBy { it.corrId }
                        .size shouldBe 5
                }
                When("Feilmeldinger håndteres") {
                    val addErrorFilenameSlots = mutableListOf<String>()
                    val addErrorMessagesSlot = mutableListOf<Pair<String, String>>()

                    coVerify(exactly = 5) {
                        slackServiceSpy.addError(capture(addErrorFilenameSlots), any<String>(), capture(addErrorMessagesSlot))
                    }

                    Then("Skal 5 feilmeldinger dannes") {
                        addErrorFilenameSlots.filter { it == fileName }.size shouldBe 5
                        addErrorMessagesSlot.size shouldBe 5
                        addErrorMessagesSlot.forEach {
                            it.first shouldBe status
                            it.second shouldBe "Organisasjon med organisasjonsnummer=xxxxxxxxx finnes ikke"
                        }
                    }
                    Then("Skal 3 feilmeldinger sendes") {
                        val sendAlertFilenameSlot = slot<String>()
                        val sendAlertMessagesSlot = slot<Map<String, List<String>>>()

                        coVerify(exactly = 1) {
                            slackClientSpy.sendMessage(any<String>(), capture(sendAlertFilenameSlot), capture(sendAlertMessagesSlot))
                        }
                        sendAlertFilenameSlot.captured shouldBe fileName
                        sendAlertMessagesSlot.captured shouldBe addErrorMessagesSlot.groupBy({ it.first }, { it.second })
                    }
                }
            }
        }

        afterSpec {
            unmockkObject(PropertiesConfig)
        }
    })

fun mottaksStatusMockHttpClient(
    mottaksStatusResponse: String,
    valideringsFeilResponse: String = MockHttpClientUtils.Responses.emptyValideringsfeilResponse(),
) = MockHttpClient().getClient(
    listOf(
        MockHttpClientUtils.MockRequestObj(mottaksStatusResponse, MockHttpClientUtils.EndepunktType.MOTTAKSSTATUS, HttpStatusCode.OK),
        MockHttpClientUtils.MockRequestObj(valideringsFeilResponse, MockHttpClientUtils.EndepunktType.HENT_VALIDERINGSFEIL, HttpStatusCode.OK),
    ),
)
