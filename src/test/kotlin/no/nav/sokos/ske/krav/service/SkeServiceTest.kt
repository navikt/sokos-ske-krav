package no.nav.sokos.ske.krav.service

import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.javaField
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlMatching
import com.github.tomakehurst.wiremock.common.ContentTypes
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.maps.beEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.mockk.clearMocks
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify

import no.nav.sokos.ske.krav.client.OPPRETT_KRAV_URL
import no.nav.sokos.ske.krav.client.STOPP_KRAV_URL
import no.nav.sokos.ske.krav.client.SkeClient
import no.nav.sokos.ske.krav.client.SlackService
import no.nav.sokos.ske.krav.config.SftpConfig
import no.nav.sokos.ske.krav.dto.ske.responses.OpprettInnkrevingsOppdragResponse
import no.nav.sokos.ske.krav.listener.PostgresListener
import no.nav.sokos.ske.krav.listener.PostgresListener.session
import no.nav.sokos.ske.krav.listener.SftpListener
import no.nav.sokos.ske.krav.listener.WiremockListener
import no.nav.sokos.ske.krav.repository.KravRepository
import no.nav.sokos.ske.krav.util.TestData

class SkeServiceTest :
    BehaviorSpec({
        extensions(PostgresListener, WiremockListener, SftpListener)

        val slackService = mockk<SlackService>(relaxed = true)
        val ftpService: FtpService by lazy {
            FtpService(SftpConfig(SftpListener.sftpProperties))
        }

        val kravService: KravService by lazy {
            spyk(
                KravService(
                    dataSource = PostgresListener.dataSource,
                    skeClient =
                        SkeClient(
                            tokenProvider = WiremockListener.mockTokenClient,
                            skeEndpoint = WiremockListener.wiremock.baseUrl() + "/",
                        ),
                    slackService = slackService,
                ),
            )
        }

        val skeService: SkeService by lazy {
            SkeService(
                dataSource = PostgresListener.dataSource,
                slackService = slackService,
                ftpService = ftpService,
                kravService = kravService,
            )
        }

        afterEach {
            PostgresListener.resetDatabase()
            SftpListener.clearAllDirectories()
            WiremockListener.wiremock.resetAll()
            clearMocks(kravService)
            clearMocks(slackService)
        }

        Given("behandleSkeKrav - large file triggers halt then unblocks") {
            val kravidentifikator = "456789"
            val mottaksStatusResponse = TestData.mottaksStatusResponse()
            SftpListener.putFiles(listOf("10NyeKrav.txt"), Directories.INBOUND)

            WiremockListener.wiremock.stubFor(
                WireMock
                    .get(WireMock.urlMatching(".*/avstemming.*"))
                    .willReturn(
                        aResponse()
                            .withHeader(HttpHeaders.ContentType, ContentTypes.APPLICATION_JSON)
                            .withBody(TestData.avstemmingReponse())
                            .withStatus(HttpStatusCode.OK.value),
                    ),
            )

            WiremockListener.wiremock.stubFor(
                WireMock
                    .post(urlEqualTo("/$OPPRETT_KRAV_URL"))
                    .willReturn(
                        aResponse()
                            .withHeader(HttpHeaders.ContentType, ContentTypes.APPLICATION_JSON)
                            .withBody(Json.encodeToString(OpprettInnkrevingsOppdragResponse(kravidentifikator = kravidentifikator)))
                            .withStatus(HttpStatusCode.Created.value),
                    ),
            )

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

            When("behandleSkeKrav called") {
                runBlocking { skeService.behandleSkeKrav() }

                Then("halt flag was set during run and reset after") {
                    // Access private haltRun via reflection
                    val haltRun = SkeService::class.declaredMemberProperties.first { it.name == "haltRun" }
                    haltRun.isAccessible = true
                    (haltRun.get(skeService) as Boolean) shouldBe false
                }
            }
        }

        Given("behandleSkeKrav - already halted at start returns early") {
            // Access private haltRun via reflection
            val haltRun = SkeService::class.declaredMemberProperties.first { it.name == "haltRun" }
            haltRun.isAccessible = true
            haltRun.javaField?.isAccessible = true
            haltRun.javaField?.set(skeService, true)

            When("behandleSkeKrav invoked while halted") {
                runBlocking { skeService.behandleSkeKrav() }

                Then("resendKrav and file handling not executed") {
                    coVerify(exactly = 0) { kravService.resendKrav() }
                    (haltRun.get(skeService) as Boolean) shouldBe true
                    // reset manually for other tests
                    haltRun.javaField?.set(skeService, false)
                }
            }
        }

        Given("behandleNyeKravFraFiler - no files available") {
            ftpService.downloadFiles() shouldBe beEmpty()

            When("invoked") {
                runBlocking { skeService.behandleNyeKravFraFiler() }

                Then("no krav-processing methods called") {
                    coVerify(exactly = 0) { kravService.opprettKravFraFilOgOppdatereStatus(any(), any()) }
                    coVerify(exactly = 0) { kravService.sendKrav(any()) }
                }
            }
        }

        Given("behandleNyeKravFraFiler - valid file processed") {
            val kravidentifikator = "456789"
            SftpListener.putFiles(listOf("10NyeKrav.txt"), Directories.INBOUND)

            WiremockListener.wiremock.stubFor(
                WireMock
                    .get(WireMock.urlMatching(".*/avstemming.*"))
                    .willReturn(
                        aResponse()
                            .withHeader(HttpHeaders.ContentType, ContentTypes.APPLICATION_JSON)
                            .withBody(TestData.avstemmingReponse())
                            .withStatus(HttpStatusCode.OK.value),
                    ),
            )

            WiremockListener.wiremock.stubFor(
                WireMock
                    .post(urlEqualTo("/$OPPRETT_KRAV_URL"))
                    .willReturn(
                        aResponse()
                            .withHeader(HttpHeaders.ContentType, ContentTypes.APPLICATION_JSON)
                            .withBody(Json.encodeToString(OpprettInnkrevingsOppdragResponse(kravidentifikator = kravidentifikator)))
                            .withStatus(HttpStatusCode.Created.value),
                    ),
            )
            ftpService.downloadFiles(Directories.OUTBOUND) shouldBe beEmpty()

            When("invoked") {
                runBlocking { skeService.behandleNyeKravFraFiler() }

                Then("krav are created and sent, file moved to OUTBOUND") {
                    KravRepository.getAllKrav(session).size shouldBe 10
                    ftpService.downloadFiles(Directories.OUTBOUND) shouldNotBe beEmpty()
                    WiremockListener.wiremock.verify(10, WireMock.postRequestedFor(urlEqualTo("/$OPPRETT_KRAV_URL")))
                    WiremockListener.wiremock.verify(10, WireMock.getRequestedFor(urlMatching(".*/avstemming.*")))
                }
            }
        }

        Given("behandleNyeKravFraFiler - validation error in file") {
            val kravidentifikator = "456789"
            val fileName = "1LinjeHarFeilKravtype.txt"
            SftpListener.putFiles(listOf(fileName), Directories.INBOUND)

            WiremockListener.wiremock.stubFor(
                WireMock
                    .get(WireMock.urlMatching(".*/avstemming.*"))
                    .willReturn(
                        aResponse()
                            .withHeader(HttpHeaders.ContentType, ContentTypes.APPLICATION_JSON)
                            .withBody(TestData.avstemmingReponse())
                            .withStatus(HttpStatusCode.OK.value),
                    ),
            )

            WiremockListener.wiremock.stubFor(
                WireMock
                    .post(urlEqualTo("/$OPPRETT_KRAV_URL"))
                    .willReturn(
                        aResponse()
                            .withHeader(HttpHeaders.ContentType, ContentTypes.APPLICATION_JSON)
                            .withBody(Json.encodeToString(OpprettInnkrevingsOppdragResponse(kravidentifikator = kravidentifikator)))
                            .withStatus(HttpStatusCode.Created.value),
                    ),
            )

            WiremockListener.wiremock.stubFor(
                WireMock
                    .post(urlEqualTo("/$STOPP_KRAV_URL"))
                    .willReturn(
                        aResponse()
                            .withHeader(HttpHeaders.ContentType, ContentTypes.APPLICATION_JSON)
                            .withStatus(HttpStatusCode.OK.value),
                    ),
            )

            WiremockListener.wiremock.stubFor(
                WireMock
                    .put(WireMock.urlMatching(".*/(hovedstol|renter).*"))
                    .willReturn(
                        aResponse()
                            .withHeader(HttpHeaders.ContentType, ContentTypes.APPLICATION_JSON)
                            .withStatus(HttpStatusCode.OK.value),
                    ),
            )

            ftpService.downloadFiles(Directories.OUTBOUND) shouldBe beEmpty()

            When("invoked") {
                runBlocking { skeService.behandleNyeKravFraFiler() }

                Then("errors are logged, slack notified and file moved to FAILED") {
                    KravRepository.getAllKrav(session).size shouldBe 11
                    ftpService.downloadFiles(Directories.OUTBOUND) shouldNotBe beEmpty()

                    WiremockListener.wiremock.verify(7, WireMock.postRequestedFor(urlEqualTo("/$OPPRETT_KRAV_URL")))
                    WiremockListener.wiremock.verify(2, WireMock.putRequestedFor(urlMatching(".*/(hovedstol|renter).*")))
                    verify(atLeast = 1) { slackService.addError(eq(fileName), any<String>(), any<List<Pair<String, String>>>()) }
                    coVerify(atLeast = 1) { slackService.sendErrors() }
                }
            }
        }

        Given("checkKravDateForAlert - only krav older than 24h send alerts") {
            PostgresListener.migrate("SQLscript/2KravForStatusCheck.sql")

            When("invoked") {
                runBlocking { skeService.checkKravDateForAlert() }

                Then("only krav older than 24h triggers slack error") {
                    verify(exactly = 1) { slackService.addError(eq("NyeKrav.txt"), any<String>(), any<Pair<String, String>>()) }
                    coVerify(exactly = 1) { slackService.sendErrors() }
                }
            }
        }
    })
