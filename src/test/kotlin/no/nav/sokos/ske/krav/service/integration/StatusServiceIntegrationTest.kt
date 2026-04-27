package no.nav.sokos.ske.krav.service.integration

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.http.HttpStatusCode
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import io.mockk.spyk

import no.nav.sokos.ske.krav.client.SkeClient
import no.nav.sokos.ske.krav.client.SlackClient
import no.nav.sokos.ske.krav.client.SlackService
import no.nav.sokos.ske.krav.config.CircuitBreakerManager
import no.nav.sokos.ske.krav.database.getAllKrav
import no.nav.sokos.ske.krav.domain.Status
import no.nav.sokos.ske.krav.listener.DBListener
import no.nav.sokos.ske.krav.listener.DBListener.dbService
import no.nav.sokos.ske.krav.listener.DBListener.feilmeldingRepository
import no.nav.sokos.ske.krav.listener.DBListener.kravRepository
import no.nav.sokos.ske.krav.security.MaskinportenAccessTokenProvider
import no.nav.sokos.ske.krav.service.DatabaseService
import no.nav.sokos.ske.krav.service.StatusService
import no.nav.sokos.ske.krav.util.http.Endpoint
import no.nav.sokos.ske.krav.util.http.MockHttpClient
import no.nav.sokos.ske.krav.util.http.MockResponse
import no.nav.sokos.ske.krav.util.http.MockResponsesBody

internal class StatusServiceIntegrationTest :
    BehaviorSpec({
        extensions(DBListener)
        beforeEach { CircuitBreakerManager.circuitBreaker.reset() }

        fun setupServices(
            client: HttpClient,
            databaseService: DatabaseService,
        ): Triple<SlackClient, SlackService, StatusService> {
            val slackClientSpy = spyk(SlackClient(client = MockHttpClient.slackClient))
            val slackServiceSpy = spyk(SlackService(slackClientSpy), recordPrivateCalls = true)
            val skeClient = SkeClient(skeEndpoint = "", client = client, tokenProvider = mockk<MaskinportenAccessTokenProvider>(relaxed = true))
            val statusServiceSpy = spyk(StatusService(DBListener.dataSource, skeClient, databaseService, slackServiceSpy, feilmeldingRepository), recordPrivateCalls = true)

            return Triple(slackClientSpy, slackServiceSpy, statusServiceSpy)
        }

        Given("Mottaksstatus trigger circuit breaker") {
            DBListener.clearDB()
            DBListener.loadInitScript("SQLscript/status/KravSomSkalOppdateres.sql")

            val avskrivKravKall = MockResponse(Endpoint.MOTTAKSSTATUS, MockResponsesBody.genericFeilResponse(), HttpStatusCode.Forbidden)
            val httpClient = MockHttpClient.client(avskrivKravKall)
            val skeClient = SkeClient(skeEndpoint = "", client = httpClient, tokenProvider = mockk<MaskinportenAccessTokenProvider>(relaxed = true))

            dbService.getAllKravForStatusCheck().shouldHaveSize(5)

            StatusService(DBListener.dataSource, skeClient, dbService, mockk<SlackService>(relaxed = true), feilmeldingRepository).getMottaksStatus()
            Then("Skal krav ikke oppdateres") {
                dbService.getAllKravForStatusCheck().shouldHaveSize(5)
            }
            CircuitBreakerManager.circuitBreaker.reset()
        }

        Given("Mottaksstatus er RESKONTROFOERT") {
            val mottaksStatusResponse = MockResponsesBody.mottaksStatusResponse(status = Status.RESKONTROFOERT.value)
            val httpClient = mottaksStatusHttpClient(mottaksStatusResponse)
            val (slackClientSpy, _, statusService) = setupServices(httpClient, dbService)

            Then("Skal mottaksstatus settes til RESKONTROFOERT i database") {
                val allKravBeforeUpdate = kravRepository.getAllKrav()
                allKravBeforeUpdate.count { it.status == Status.RESKONTROFOERT } shouldBe 3

                statusService.getMottaksStatus()

                val allKravAfterUpdate = kravRepository.getAllKrav()
                allKravAfterUpdate.count { it.status == Status.RESKONTROFOERT } shouldBe 8
            }
            Then("Alert skal ikke sendes") {
                coVerify(exactly = 0) {
                    slackClientSpy.sendMessage(any<String>(), any<String>(), any<Map<String, List<String>>>(), any<List<String>>(), any())
                }
            }
        }
        Given("Mottaksstatus er VALIDERINGSFEIL") {
            DBListener.clearDB()
            val fileName = "KravSomSkalOppdateres.sql"
            DBListener.loadInitScript("SQLscript/status/$fileName")
            val status = "ORGANISASJONSNUMMER_FINNES_IKKE"
            val mottaksStatusResponse = MockResponsesBody.mottaksStatusResponse(status = Status.VALIDERINGSFEIL_MOTTAKSSTATUS.value)
            val valideringsFeilResponse = MockResponsesBody.valideringsfeilResponse(status, "Organisasjon med organisasjonsnummer=xxxxxxxxx finnes ikke")
            val httpClient = mottaksStatusHttpClient(mottaksStatusResponse, valideringsFeilResponse)

            val (slackClientSpy, slackServiceSpy, statusService) = setupServices(httpClient, dbService)

            feilmeldingRepository.getAllFeilmeldinger().shouldHaveSize(0)
            dbService.getAllKravForStatusCheck().shouldHaveSize(5)

            statusService.getMottaksStatus()

            Then("Skal feilmelding lagres i Feilmelding tabell") {
                val feilmeldinger = feilmeldingRepository.getAllFeilmeldinger()
                feilmeldinger.shouldHaveSize(5)
                feilmeldinger.forEach {
                    it.error shouldBe status
                    it.melding shouldBe "Organisasjon med organisasjonsnummer=xxxxxxxxx finnes ikke"
                }
            }

            Then("Mottaksstatus skal settes til VALIDERINGSFEIL i database") {
                kravRepository
                    .getAllKrav()
                    .filter { it.status == Status.VALIDERINGSFEIL_MOTTAKSSTATUS }
                    .distinctBy { it.corrId }
                    .shouldHaveSize(5)
            }

            When("Feilmeldinger håndteres") {
                val addErrorFilenameSlots = mutableListOf<String>()
                val addErrorMessagesSlot = mutableListOf<Pair<String, String>>()

                coVerify(exactly = 5) {
                    slackServiceSpy.addError(capture(addErrorFilenameSlots), any<String>(), capture(addErrorMessagesSlot))
                }

                Then("Skal 5 feilmeldinger dannes") {
                    addErrorFilenameSlots.filter { it == fileName }.shouldHaveSize(5)
                    addErrorMessagesSlot.shouldHaveSize(5)
                    addErrorMessagesSlot.forEach {
                        it.first shouldBe status
                        it.second shouldBe "Organisasjon med organisasjonsnummer=xxxxxxxxx finnes ikke"
                    }
                }
                Then("Skal 3 feilmeldinger sendes") {
                    val sendAlertFilenameSlot = slot<String>()
                    val sendAlertMessagesSlot = slot<Map<String, List<String>>>()

                    coVerify(exactly = 1) {
                        slackClientSpy.sendMessage(any<String>(), capture(sendAlertFilenameSlot), capture(sendAlertMessagesSlot), any<List<String>>(), any())
                    }
                    sendAlertFilenameSlot.captured shouldBe fileName
                    sendAlertMessagesSlot.captured shouldBe addErrorMessagesSlot.groupBy({ it.first }, { it.second })
                }
            }
        }
    })

fun mottaksStatusHttpClient(
    mottaksStatusResponse: String,
    valideringsFeilResponse: String = MockResponsesBody.emptyValideringsfeilResponse(),
) = MockHttpClient.client(
    MockResponse(Endpoint.MOTTAKSSTATUS, mottaksStatusResponse),
    MockResponse(Endpoint.HENT_VALIDERINGSFEIL, valideringsFeilResponse),
)
