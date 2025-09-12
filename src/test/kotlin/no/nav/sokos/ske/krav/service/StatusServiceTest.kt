package no.nav.sokos.ske.krav.service

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.common.ContentTypes
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import io.mockk.spyk

import no.nav.sokos.ske.krav.client.SkeClient
import no.nav.sokos.ske.krav.client.SlackClient
import no.nav.sokos.ske.krav.client.SlackService
import no.nav.sokos.ske.krav.domain.Status
import no.nav.sokos.ske.krav.listener.PostgresListener
import no.nav.sokos.ske.krav.listener.PostgresListener.feilmeldingRepository
import no.nav.sokos.ske.krav.listener.PostgresListener.kravRepository
import no.nav.sokos.ske.krav.listener.WiremockListener
import no.nav.sokos.ske.krav.util.TestData

class StatusServiceTest :
    BehaviorSpec({
        extensions(PostgresListener, WiremockListener)

        val slackClient = mockk<SlackClient>(relaxed = true)
        val slackService = spyk(SlackService(slackClient))
        val statusService: StatusService by lazy {
            StatusService(
                dataSource = PostgresListener.dataSource,
                skeClient =
                    SkeClient(
                        tokenProvider = WiremockListener.mockTokenClient,
                        skeEndpoint = WiremockListener.wiremock.baseUrl() + "/",
                    ),
                slackService = slackService,
            )
        }

        Given("Mottaksstatus er RESKONTROFOERT") {
            PostgresListener.migrate("SQLscript/KravSomSkalOppdateres.sql")
            val mottaksStatusResponse = TestData.mottaksStatusResponse(status = Status.RESKONTROFOERT.value)

            WiremockListener.wiremock.stubFor(
                WireMock
                    .get(WireMock.urlMatching(".*/mottaksstatus.*"))
                    .willReturn(
                        aResponse()
                            .withHeader(HttpHeaders.ContentType, ContentTypes.APPLICATION_JSON)
                            .withBody(mottaksStatusResponse)
                            .withStatus(HttpStatusCode.OK.value),
                    ),
            )

            Then("Skal mottaksstatus settes til RESKONTROFOERT i database") {
                val allKravBeforeUpdate = kravRepository.getAllKrav()
                allKravBeforeUpdate.filter { it.status == Status.RESKONTROFOERT.value }.size shouldBe 3

                statusService.getMottaksStatus()

                val allKravAfterUpdate = kravRepository.getAllKrav()
                allKravAfterUpdate.filter { it.status == Status.RESKONTROFOERT.value }.size shouldBe 8
            }

            Then("Alert skal ikke sendes") {
                coVerify(exactly = 0) {
                    slackClient.sendMessage(any<String>(), any<String>(), any<Map<String, List<String>>>())
                }
            }
        }

        Given("Mottaksstatus er VALIDERINGSFEIL") {
            val fileName = "KravSomSkalOppdateres.sql"
            PostgresListener.migrate("SQLscript/$fileName")

            val status = "ORGANISASJONSNUMMER_FINNES_IKKE"
            val mottaksStatusResponse = TestData.mottaksStatusResponse(status = Status.VALIDERINGSFEIL_MOTTAKSSTATUS.value)
            val valideringsFeilRespons = TestData.valideringsfeilResponse(status, "Organisasjon med organisasjonsnummer=xxxxxxxxx finnes ikke")

            WiremockListener.wiremock.stubFor(
                WireMock
                    .get(WireMock.urlMatching(".*/mottaksstatus.*"))
                    .willReturn(
                        aResponse()
                            .withHeader(HttpHeaders.ContentType, ContentTypes.APPLICATION_JSON)
                            .withBody(mottaksStatusResponse)
                            .withStatus(HttpStatusCode.OK.value),
                    ),
            )
            WiremockListener.wiremock.stubFor(
                WireMock
                    .get(WireMock.urlMatching(".*/valideringsfeil.*"))
                    .willReturn(
                        aResponse()
                            .withHeader(HttpHeaders.ContentType, ContentTypes.APPLICATION_JSON)
                            .withBody(valideringsFeilRespons)
                            .withStatus(HttpStatusCode.OK.value),
                    ),
            )

            feilmeldingRepository.getAllFeilmeldinger().size shouldBe 0
            kravRepository.getAllKravForStatusCheck().size shouldBe 5

            statusService.getMottaksStatus()

            Then("Skal feilmelding lagres i Feilmelding tabell") {
                val feilmeldinger = feilmeldingRepository.getAllFeilmeldinger()
                feilmeldinger.size shouldBe 5
                feilmeldinger.forEach {
                    it.error shouldBe status
                    it.melding shouldBe "Organisasjon med organisasjonsnummer=xxxxxxxxx finnes ikke"
                }
            }

            Then("Mottaksstatus skal settes til VALIDERINGSFEIL i database") {
                kravRepository
                    .getAllKrav()
                    .filter { it.status == Status.VALIDERINGSFEIL_MOTTAKSSTATUS.value }
                    .distinctBy { it.corrId }
                    .size shouldBe 5
            }

            When("Feilmeldinger håndteres") {
                val addErrorFilenameSlots = mutableListOf<String>()
                val addErrorMessagesSlot = mutableListOf<Pair<String, String>>()

                coVerify(exactly = 5) {
                    slackService.addError(capture(addErrorFilenameSlots), any<String>(), capture(addErrorMessagesSlot))
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
                        slackClient.sendMessage(any<String>(), capture(sendAlertFilenameSlot), capture(sendAlertMessagesSlot))
                    }
                    sendAlertFilenameSlot.captured shouldBe fileName
                    sendAlertMessagesSlot.captured shouldBe addErrorMessagesSlot.groupBy({ it.first }, { it.second })
                }
            }
        }
    })
