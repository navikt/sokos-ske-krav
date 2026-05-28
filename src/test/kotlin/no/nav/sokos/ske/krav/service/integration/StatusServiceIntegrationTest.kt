package no.nav.sokos.ske.krav.service.integration

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.core.test.TestCase
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.http.HttpStatusCode
import io.mockk.clearMocks
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import io.mockk.spyk
import org.slf4j.LoggerFactory

import no.nav.sokos.ske.krav.client.SkeClient
import no.nav.sokos.ske.krav.client.SlackClient
import no.nav.sokos.ske.krav.client.SlackService
import no.nav.sokos.ske.krav.config.CircuitBreakerManager
import no.nav.sokos.ske.krav.domain.Status
import no.nav.sokos.ske.krav.listener.DBListener
import no.nav.sokos.ske.krav.listener.DBListener.dataSource
import no.nav.sokos.ske.krav.listener.DBListener.feilmeldingRepository
import no.nav.sokos.ske.krav.listener.DBListener.kravRepository
import no.nav.sokos.ske.krav.security.MaskinportenAccessTokenProvider
import no.nav.sokos.ske.krav.service.StatusService
import no.nav.sokos.ske.krav.util.getAllKrav
import no.nav.sokos.ske.krav.util.http.Endpoint
import no.nav.sokos.ske.krav.util.http.MockHttpClient
import no.nav.sokos.ske.krav.util.http.MockResponse
import no.nav.sokos.ske.krav.util.http.MockResponsesBody
import no.nav.sokos.ske.krav.util.transaction

internal class StatusServiceIntegrationTest :
    BehaviorSpec({
        extensions(DBListener)

        val slackClient =
            mockk<SlackClient> {
                coJustRun { sendMessage(any(), any(), any(), any(), any()) }
            }

        val slackService = spyk(SlackService(slackClient))

        val statusServiceLogger = LoggerFactory.getLogger(StatusService::class.java) as Logger
        val logAppender = ListAppender<ILoggingEvent>()

        beforeContainer { testCase: TestCase ->
            if (testCase.name.prefix == "Given: ") {
                CircuitBreakerManager.circuitBreaker.reset()
                clearMocks(slackClient, answers = false)

                DBListener.clearDB()
                DBListener.loadInitScripts("SQLscript/status/KravSomSkalOppdateres.sql")
            }
        }

        fun statusService(httpClient: HttpClient) =
            StatusService(
                dataSource,
                SkeClient(skeEndpoint = "", client = httpClient, tokenProvider = mockk<MaskinportenAccessTokenProvider>(relaxed = true)),
                slackService,
                feilmeldingRepository,
                kravRepository,
            )

        Given("Mottaksstatus trigger circuit breaker") {
            val avskrivKravKall = MockResponse(Endpoint.MOTTAKSSTATUS, MockResponsesBody.genericFeilResponse(), HttpStatusCode.Forbidden)
            val httpClient = MockHttpClient.client(avskrivKravKall)

            kravRepository.getAllKravForStatusCheck().shouldHaveSize(5)

            statusService(httpClient).getMottaksStatus()
            Then("Skal krav ikke oppdateres") {
                kravRepository.getAllKravForStatusCheck().shouldHaveSize(5)
            }
            CircuitBreakerManager.circuitBreaker.reset()
        }

        Given("Mottaksstatus er RESKONTROFOERT") {
            val mottaksStatusResponse = MockResponsesBody.mottaksStatusResponse(status = Status.RESKONTROFOERT.value)
            val httpClient = mottaksStatusHttpClient(mottaksStatusResponse)
            val statusService = statusService(httpClient)

            Then("Skal mottaksstatus settes til RESKONTROFOERT i database") {
                val allKravBeforeUpdate =
                    dataSource.transaction { session ->
                        kravRepository.getAllKrav(session)
                    }
                allKravBeforeUpdate.count { it.status == Status.RESKONTROFOERT } shouldBe 3

                statusService.getMottaksStatus()

                val allKravAfterUpdate =
                    dataSource.transaction { session ->
                        kravRepository.getAllKrav(session)
                    }
                allKravAfterUpdate.count { it.status == Status.RESKONTROFOERT } shouldBe 8
            }
            Then("Alert skal ikke sendes") {
                coVerify(exactly = 0) {
                    slackClient.sendMessage(any<String>(), any<String>(), any<Map<String, List<String>>>(), any<List<String>>(), any())
                }
            }
        }

        Given("Mottaks status oppdateres") {
            logAppender.start()
            statusServiceLogger.addAppender(logAppender)

            val mottaksStatusResponse = MockResponsesBody.mottaksStatusResponse(status = Status.MIGRERT.value)
            val httpClient = mottaksStatusHttpClient(mottaksStatusResponse)
            val statusService = statusService(httpClient)

            Then("Vi logger både reskontroført og migrert krav") {
                val allKravBeforeUpdate =
                    dataSource.transaction { session ->
                        kravRepository.getAllKrav(session)
                    }
                allKravBeforeUpdate.count { it.status == Status.MIGRERT } shouldBe 0

                statusService.getMottaksStatus()

                val allKravAfterUpdate =
                    dataSource.transaction { session ->
                        kravRepository.getAllKrav(session)
                    }

                allKravAfterUpdate.count { it.status == Status.MIGRERT } shouldBe 5

                val messages = logAppender.list.map { it.formattedMessage }
                messages.filter { it == "Antall reskontroførte krav: 5" }.shouldHaveSize(1)

                statusServiceLogger.detachAppender(logAppender)
                logAppender.stop()
            }
        }

        Given("Mottaksstatus er VALIDERINGSFEIL") {
            val fileName = "KravSomSkalOppdateres.sql"
            val status = "ORGANISASJONSNUMMER_FINNES_IKKE"
            val mottaksStatusResponse = MockResponsesBody.mottaksStatusResponse(status = Status.VALIDERINGSFEIL_MOTTAKSSTATUS.value)
            val valideringsFeilResponse = MockResponsesBody.valideringsfeilResponse(status, "Organisasjon med organisasjonsnummer=xxxxxxxxx finnes ikke")
            val httpClient = mottaksStatusHttpClient(mottaksStatusResponse, valideringsFeilResponse)

            feilmeldingRepository.getAllFeilmeldinger().shouldBeEmpty()
            kravRepository.getAllKravForStatusCheck().shouldHaveSize(5)

            statusService(httpClient).getMottaksStatus()

            Then("Skal feilmelding lagres i Feilmelding tabell") {
                val feilmeldinger = feilmeldingRepository.getAllFeilmeldinger()
                feilmeldinger.shouldHaveSize(5)
                feilmeldinger.forEach {
                    it.error shouldBe status
                    it.melding shouldBe "Organisasjon med organisasjonsnummer=xxxxxxxxx finnes ikke"
                }
            }

            Then("Mottaksstatus skal settes til VALIDERINGSFEIL i database") {
                val allKrav =
                    dataSource.transaction { session ->
                        kravRepository.getAllKrav(session)
                    }

                allKrav
                    .filter { it.status == Status.VALIDERINGSFEIL_MOTTAKSSTATUS }
                    .distinctBy { it.corrId }
                    .shouldHaveSize(5)
            }

            When("Feilmeldinger håndteres") {
                val addErrorFilenameSlots = mutableListOf<String>()
                val addErrorMessagesSlot = mutableListOf<Pair<String, String>>()

                coVerify(exactly = 5) {
                    slackService.addError(capture(addErrorFilenameSlots), any<String>(), capture(addErrorMessagesSlot))
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
                        slackClient.sendMessage(any<String>(), capture(sendAlertFilenameSlot), capture(sendAlertMessagesSlot), any<List<String>>(), any())
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
