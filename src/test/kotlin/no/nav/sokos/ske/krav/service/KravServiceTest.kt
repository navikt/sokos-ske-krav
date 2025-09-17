package no.nav.sokos.ske.krav.service

import kotlinx.serialization.json.Json

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.common.ContentTypes
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.mockk.mockk

import no.nav.sokos.ske.krav.client.OPPRETT_KRAV_URL
import no.nav.sokos.ske.krav.client.STOPP_KRAV_URL
import no.nav.sokos.ske.krav.client.SkeClient
import no.nav.sokos.ske.krav.client.SlackService
import no.nav.sokos.ske.krav.domain.ENDRING_HOVEDSTOL
import no.nav.sokos.ske.krav.domain.Status
import no.nav.sokos.ske.krav.dto.ske.responses.OpprettInnkrevingsOppdragResponse
import no.nav.sokos.ske.krav.listener.PostgresListener
import no.nav.sokos.ske.krav.listener.PostgresListener.kravRepository
import no.nav.sokos.ske.krav.listener.WiremockListener
import no.nav.sokos.ske.krav.util.TestData

class KravServiceTest :
    BehaviorSpec({
        extensions(PostgresListener, WiremockListener)

        val kravService: KravService by lazy {
            KravService(
                dataSource = PostgresListener.dataSource,
                skeClient =
                    SkeClient(
                        tokenProvider = WiremockListener.mockTokenClient,
                        skeEndpoint = WiremockListener.wiremock.baseUrl() + "/",
                    ),
                slackService = mockk<SlackService>(relaxed = true),
            )
        }

        Given("sendKrav med sendAllOpprettKrav - 2 Nye krav skal opprettes") {
            val kravidentifikator = "456789"
            PostgresListener.migrate("SQLscript/2NyeKrav.sql")

            val kravSomSkalSendes = kravRepository.getAllKrav()
            kravSomSkalSendes.size shouldBe 2

            When("Response fra SKE er OK") {
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

                kravService.sendKrav(kravSomSkalSendes)

                Then("Skal kravene oppdateres med SKE kravidentifikator") {
                    kravRepository.getAllKrav().forEach { krav ->
                        krav.status shouldBe Status.KRAV_SENDT.value
                        krav.kravidentifikatorSKE shouldBe kravidentifikator
                    }
                }
            }

            When("Response fra SKE ikke er OK") {
                WiremockListener.wiremock.stubFor(
                    WireMock
                        .post(urlEqualTo("/$OPPRETT_KRAV_URL"))
                        .willReturn(
                            aResponse()
                                .withHeader(HttpHeaders.ContentType, ContentTypes.APPLICATION_JSON)
                                .withBody(TestData.feilResponse())
                                .withStatus(HttpStatusCode.BadRequest.value),
                        ),
                )

                kravService.sendKrav(kravSomSkalSendes)

                Then("Skal kravene ikke oppdateres") {

                    kravRepository.getAllKrav().run {
                        size shouldBe 2
                        filter { it.saksnummerNAV == "1111-navsaksnr" }.size shouldBe 1
                        filter { it.saksnummerNAV == "2222-navsaksnr" }.size shouldBe 1
                        filter { it.kravidentifikatorSKE == "" }.size shouldBe 2
                    }
                }
            }
        }

        Given("sendKrav med sendAllEndreKrav - to endringskrav (rente + hovedstol) suksess") {
            PostgresListener.resetDatabase()
            PostgresListener.migrate("SQLscript/2EndringsKrav.sql")

            val endringsKrav = kravRepository.getAllKrav()
            endringsKrav.size shouldBe 2
            val originalIds = endringsKrav.associate { it.corrId to it.kravidentifikatorSKE }

            When("Begge endringskall returnerer 200") {
                WiremockListener.wiremock.stubFor(
                    WireMock
                        .put(WireMock.urlMatching(".*/hovedstol.*"))
                        .willReturn(
                            aResponse()
                                .withHeader(HttpHeaders.ContentType, ContentTypes.APPLICATION_JSON)
                                .withStatus(HttpStatusCode.OK.value),
                        ),
                )
                WiremockListener.wiremock.stubFor(
                    WireMock
                        .put(WireMock.urlMatching(".*/renter.*"))
                        .willReturn(
                            aResponse()
                                .withHeader(HttpHeaders.ContentType, ContentTypes.APPLICATION_JSON)
                                .withStatus(HttpStatusCode.OK.value),
                        ),
                )

                kravService.sendKrav(endringsKrav)

                Then("Status blir KRAV_SENDT for begge og kravidentifikator uendret") {
                    kravRepository.getAllKrav().forEach {
                        it.status shouldBe Status.KRAV_SENDT.value
                        it.kravidentifikatorSKE shouldBe originalIds[it.corrId]
                    }
                }
            }

            When("Én endring feiler med 404 og den andre lykkes") {
                WiremockListener.wiremock.resetAll()
                WiremockListener.wiremock.stubFor(
                    WireMock
                        .put(WireMock.urlMatching(".*/hovedstol.*"))
                        .willReturn(
                            aResponse()
                                .withHeader(HttpHeaders.ContentType, ContentTypes.APPLICATION_JSON)
                                .withStatus(HttpStatusCode.NotFound.value),
                        ),
                )
                WiremockListener.wiremock.stubFor(
                    WireMock
                        .put(WireMock.urlMatching(".*/renter.*"))
                        .willReturn(
                            aResponse()
                                .withHeader(HttpHeaders.ContentType, ContentTypes.APPLICATION_JSON)
                                .withStatus(HttpStatusCode.OK.value),
                        ),
                )

                kravService.sendKrav(kravRepository.getAllKrav())

                Then("Begge endringskrav harmoniseres til FEIL_SENDT") {
                    kravRepository.getAllKrav().forEach { krav ->
                        when {
                            krav.kravtype == ENDRING_HOVEDSTOL -> krav.status shouldBe Status.HTTP404_ANNEN_IKKE_FUNNET.value
                            else -> krav.status shouldBe Status.KRAV_SENDT.value
                        }
                    }
                }
            }
        }

        Given("sendKrav med sendAllStoppKrav - suksess og feil") {
            PostgresListener.resetDatabase()
            PostgresListener.migrate("SQLscript/1StoppKrav.sql")

            val stoppKrav = kravRepository.getAllKrav()
            stoppKrav.size shouldBe 1

            When("Stopp-endepunkt returnerer 200") {
                WiremockListener.wiremock.stubFor(
                    WireMock
                        .post(urlEqualTo("/$STOPP_KRAV_URL"))
                        .willReturn(
                            aResponse()
                                .withHeader(HttpHeaders.ContentType, ContentTypes.APPLICATION_JSON)
                                .withStatus(HttpStatusCode.OK.value),
                        ),
                )

                kravService.sendKrav(stoppKrav)

                Then("Status blir KRAV_SENDT") {
                    kravRepository.getAllKrav().first().status shouldBe Status.KRAV_SENDT.value
                }
            }

            When("Stopp-endepunkt returnerer 400") {
                WiremockListener.wiremock.resetAll()
                WiremockListener.wiremock.stubFor(
                    WireMock
                        .post(urlEqualTo("/$STOPP_KRAV_URL"))
                        .willReturn(
                            aResponse()
                                .withHeader(HttpHeaders.ContentType, ContentTypes.APPLICATION_JSON)
                                .withBody(TestData.feilResponse())
                                .withStatus(HttpStatusCode.BadRequest.value),
                        ),
                )

                kravService.sendKrav(kravRepository.getAllKrav())

                Then("Status blir FEIL_SENDT") {
                    kravRepository.getAllKrav().first().status shouldBe Status.HTTP400_UGYLDIG_FORESPORSEL.value
                }
            }
        }

        Given("resendKrav - feilede opprett krav blir resendte og lykkes") {
            PostgresListener.resetDatabase()
            PostgresListener.migrate("SQLscript/2NyeKrav.sql")

            val krav = kravRepository.getAllKrav()
            krav.size shouldBe 2

            When("Første forsøk feiler (500)") {
                WiremockListener.wiremock.stubFor(
                    WireMock
                        .post(urlEqualTo("/$OPPRETT_KRAV_URL"))
                        .willReturn(
                            aResponse()
                                .withHeader(HttpHeaders.ContentType, ContentTypes.APPLICATION_JSON)
                                .withBody(TestData.feilResponse())
                                .withStatus(HttpStatusCode.InternalServerError.value),
                        ),
                )

                kravService.sendKrav(krav)

                Then("Status settes til FEIL_SENDT og ingen kravidentifikator lagres") {
                    kravRepository.getAllKrav().forEach {
                        it.status shouldBe Status.HTTP500_INTERN_TJENERFEIL.value
                        it.kravidentifikatorSKE shouldBe ""
                    }
                }
            }

            When("resendKrav - andre forsøk lykkes (201)") {
                val kravidentifikator = "998877"
                WiremockListener.wiremock.resetAll()

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

                kravService.resendKrav()

                Then("Begge krav får status KRAV_SENDT og får ny kravidentifikator") {
                    kravRepository.getAllKrav().forEach {
                        it.status shouldBe Status.KRAV_SENDT.value
                        it.kravidentifikatorSKE shouldBe kravidentifikator
                    }
                }
            }
        }

        Given("getKravListe returns unsent krav") {
            PostgresListener.resetDatabase()
            PostgresListener.migrate("SQLscript/2IkkeSentKrav.sql")

            When("calling getKravListe with IKKE_SENT_KRAV") {
                val result = kravService.getKravListe(IKKE_SENT_KRAV)
                Then("should return unsent krav") {
                    result.size shouldBe 2
                    result.all { it.status != "KRAV_SENDT" } shouldBe true
                }
            }
        }

        Given("opprettKravFraFilOgOppdatereStatus inserts krav and updates status") {
            val kravlinje = TestData.getKravlinjerTestData()
            val fileName = "NyeKrav.txt"

            When("calling opprettKravFraFilOgOppdatereStatus med skeKravidentifikator ikke funnet") {
                PostgresListener.resetDatabase()
                WiremockListener.wiremock.resetAll()
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

                kravService.opprettKravFraFilOgOppdatereStatus(kravlinje, fileName)
                Then("should update krav with SKE kravidentifikator") {
                    val krav = kravRepository.getAllKrav().first { it.saksnummerNAV == "saksnummer" }
                    krav.kravidentifikatorSKE shouldBe "1234"
                }
            }

            When("calling opprettKravFraFilOgOppdatereStatus med skeKravidentifikator funnet") {
                PostgresListener.resetDatabase()
                PostgresListener.migrate("SQLscript/gamleKrav.sql")

                WiremockListener.wiremock.resetAll()

                kravService.opprettKravFraFilOgOppdatereStatus(kravlinje, "")
                Then("should update krav with SKE kravidentifikator") {
                    val krav = kravRepository.getAllKrav().first { it.saksnummerNAV == "saksnummer" }
                    krav.kravidentifikatorSKE shouldBe "abc"
                }
            }
        }
    })
