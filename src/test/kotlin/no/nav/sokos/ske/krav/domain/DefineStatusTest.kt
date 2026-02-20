package no.nav.sokos.ske.krav.domain

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.statement.bodyAsText
import io.mockk.mockk

import no.nav.sokos.ske.krav.dto.ske.responses.FeilResponse
import no.nav.sokos.ske.krav.util.AVSKREVET_KRAV_KAN_IKKE_AVSKRIVES
import no.nav.sokos.ske.krav.util.AVSKREVET_KRAV_KAN_IKKE_ENDRES
import no.nav.sokos.ske.krav.util.KRAV_EKSISTERER_IKKE
import no.nav.sokos.ske.krav.util.KRAV_ER_ALLEREDE_AVSKREVET
import no.nav.sokos.ske.krav.util.KRAV_ER_AVSKREVET
import no.nav.sokos.ske.krav.util.KRAV_ER_IKKE_RESKONTROFOERT
import no.nav.sokos.ske.krav.util.MockHttpClientUtils.Responses.generateFeilResponse
import no.nav.sokos.ske.krav.util.OPPDRAGSGIVERS_KRAVIDENTIFIKATOR_EKSISTERER
import no.nav.sokos.ske.krav.util.RequestResult
import no.nav.sokos.ske.krav.util.UGYLDIG_KRAVIDENTIFIKATOR
import no.nav.sokos.ske.krav.util.UGYLDIG_TILLEGGSINFORMASJON
import no.nav.sokos.ske.krav.util.defineStatusWithError
import no.nav.sokos.ske.krav.util.encodeToString
import no.nav.sokos.ske.krav.util.mockHttpResponse

internal class DefineStatusTest :
    FunSpec(
        {
            suspend fun createRequestResult(
                responseCode: Int,
                feilResponsType: String = "",
            ): RequestResult {
                val feilResponse = generateFeilResponse(feilResponsType, responseCode)
                val response = mockHttpResponse(responseCode, feilResponseType = feilResponsType, body = feilResponse)
                return RequestResult(
                    response,
                    mockk(),
                    "",
                    "",
                    defineStatusWithError(response.bodyAsText(), response.status).first,
                )
            }

            test("Når responsekode er 400 skal krav ha status Status.UGYLDIG_FORESPORSEL_400") {
                createRequestResult(400).status shouldBe Status.HTTP400_UGYLDIG_FORESPORSEL
            }

            test("Når responsekode er 400 og typen inneholder UGYLDIG_KRAVIDENTIFIKATOR skal krav ha status Status.HTTP400_UGYLDIG_KRAVIDENTIFIKATOR") {
                createRequestResult(400, "test $UGYLDIG_KRAVIDENTIFIKATOR")
                    .status shouldBe Status.HTTP400_UGYLDIG_KRAVIDENTIFIKATOR
            }

            test("Når responsekode er 400 og typen inneholder UGYLDIG_TILLEGGSINFORMASJON skal krav ha status Status.HTTP400_UGYLDIG_TILLEGGSINFORMASJON") {
                createRequestResult(400, "test $UGYLDIG_TILLEGGSINFORMASJON").status shouldBe Status.HTTP400_UGYLDIG_TILLEGGSINFORMASJON
            }

            test("Når responsekode er 401 skal krav ha status Status.FEIL_AUTENTISERING_401") {
                createRequestResult(401).status shouldBe Status.HTTP401_FEIL_AUTENTISERING
            }

            test("Når responsekode er 403 skal krav ha status Status.INGEN_TILGANG_403") {
                createRequestResult(403).status shouldBe Status.HTTP403_INGEN_TILGANG
            }

            test("Når responsekode er 404 og typen inneholder KRAV_EKSISTERER_IKKE, skal krav ha status Status.FANT_IKKE_SAKSREF_404") {
                createRequestResult(404, "test $KRAV_EKSISTERER_IKKE").status shouldBe Status.HTTP404_FANT_IKKE_SAKSREF
            }

            test("Når responsekode er 404 og typen ikke gjenkjennes, skal krav ha status Status.ANNEN_IKKE_FUNNET_404") {
                createRequestResult(404).status shouldBe Status.HTTP404_ANNEN_IKKE_FUNNET
            }

            test("Når responsekode er 404 og typen inneholder KRAV_ER_IKKE_RESKONTROFOERT skal krav ha status Status.HTTP404_KRAV_ER_IKKE_RESKONTROFORT") {
                createRequestResult(404, "test $KRAV_ER_IKKE_RESKONTROFOERT").status shouldBe Status.HTTP404_KRAV_ER_IKKE_RESKONTROFORT
            }

            test("Når responsekode er 406 skal krav ha status Status.FEIL_MEDIETYPE_406") {
                createRequestResult(406).status shouldBe Status.HTTP406_FEIL_MEDIETYPE
            }

            test("Når responsekode er 409 og typen inneholder KRAV_ER_AVSKREVET eller KRAV_ER_ALLEREDE_AVSKREVET skal krav ha status Status.KRAV_ER_AVSKREVET_409") {
                createRequestResult(409, "test $KRAV_ER_AVSKREVET").status shouldBe Status.HTTP409_KRAV_ER_AVSKREVET
                createRequestResult(409, "test $KRAV_ER_ALLEREDE_AVSKREVET").status shouldBe Status.HTTP409_KRAV_ER_AVSKREVET
            }

            test("Når responsekode er 409 og typen inneholder AVSKREVET_KRAV_KAN_IKKE_ENDRES skal krav ha status Status.HTTP409_AVSKREVET_KRAV_KAN_IKKE_ENDRES") {
                createRequestResult(409, "test $AVSKREVET_KRAV_KAN_IKKE_ENDRES").status shouldBe Status.HTTP409_AVSKREVET_KRAV_KAN_IKKE_ENDRES
            }

            test("Når responsekode er 409 og typen inneholder AVSKREVET_KRAV_KAN_IKKE_AVSKRIVES skal krav ha status Status.HTTP409_AVSKREVET_KRAV_KAN_IKKE_AVSKRIVES") {
                createRequestResult(409, "test $AVSKREVET_KRAV_KAN_IKKE_AVSKRIVES").status shouldBe Status.HTTP409_AVSKREVET_KRAV_KAN_IKKE_AVSKRIVES
            }

            test("Når responsekode er 409 og typen inneholder OPPDRAGSGIVERS_KRAVIDENTIFIKATOR_EKSISTERER skal krav ha status Status.HTTP409_KRAVIDENTIFIKATOR_EKSISTERER") {
                createRequestResult(409, "test $OPPDRAGSGIVERS_KRAVIDENTIFIKATOR_EKSISTERER").status shouldBe Status.HTTP409_KRAVIDENTIFIKATOR_EKSISTERER
            }

            test("Når responsekode er 409 og typen inneholder KRAV_ER_IKKE_RESKONTROFOERT skal krav ha status Status.HTTP409_KRAV_ER_IKKE_RESKONTROFORT_RESEND") {
                createRequestResult(409, "test $KRAV_ER_IKKE_RESKONTROFOERT").status shouldBe Status.HTTP409_KRAV_ER_IKKE_RESKONTROFORT_RESEND
            }

            test("Når responsekode er 409 og typen ikke gjenkjennes skal krav ha status Status.ANNEN_KONFLIKT_409") {
                createRequestResult(409).status shouldBe Status.HTTP409_ANNEN_KONFLIKT
            }

            test("Når responsekode er 422 skal krav ha status Status.VALIDERINGSFEIL_422") {
                createRequestResult(422).status shouldBe Status.HTTP422_VALIDERINGSFEIL
            }

            test("Når responsekode er 500 skal krav ha status Status.INTERN_TJENERFEIL_500") {
                createRequestResult(500).status shouldBe Status.HTTP500_INTERN_TJENERFEIL
            }

            test("Når responsekode er 503 skal krav ha status Status.UTILGJENGELIG_TJENESTE_503") {
                createRequestResult(503).status shouldBe Status.HTTP503_UTILGJENGELIG_TJENESTE
            }

            test("Når responsekode er i 300-serien og ikke spesifikt dekket så skal krav ha status Status.REDIRECTION_FEIL_300") {
                createRequestResult(301).status shouldBe Status.HTTP300_REDIRECTION_FEIL
            }

            test("Når responsekode er i 400-serien og ikke spesifikt dekket så skal krav ha status Status.ANNEN_KLIENT_FEIL_400") {
                createRequestResult(420).status shouldBe Status.HTTP400_ANNEN_KLIENT_FEIL
            }

            test("Når responsekode er i 500-serien og ikke spesifikt dekket så skal krav ha status Status.ANNEN_SERVER_FEIL_500") {
                createRequestResult(502).status shouldBe Status.HTTP500_ANNEN_SERVER_FEIL
            }

            test("Når responsekode ikke er dekket så skal krav ha status Status.UKJENT_FEIL") {
                createRequestResult(102).status shouldBe Status.UKJENT_FEIL
            }

            // Tests for defineStatusWithError
            context("defineStatusWithError") {
                test("Når response er success (200) skal defineStatusWithError returnere KRAV_SENDT og null FeilResponse") {
                    val response = mockHttpResponse(200, body = "")
                    val (status, feilResponse) = defineStatusWithError(response.bodyAsText(), response.status)

                    status shouldBe Status.KRAV_SENDT
                    feilResponse shouldBe null
                }

                test("Når response er 404 med FeilResponse skal defineStatusWithError returnere riktig status og FeilResponse") {
                    val expectedFeilResponse =
                        FeilResponse(
                            type = KRAV_EKSISTERER_IKKE,
                            title = "Not Found",
                            status = 404,
                            detail = "Innkrevingsoppdrag eksisterer ikke",
                            instance = "/innkrevingsoppdrag/123",
                        )
                    val response = mockHttpResponse(404, body = expectedFeilResponse.encodeToString())
                    val (status, feilResponse) = defineStatusWithError(response.bodyAsText(), response.status)

                    status shouldBe Status.HTTP404_FANT_IKKE_SAKSREF
                    feilResponse shouldBe expectedFeilResponse
                }

                test("Når response er 403 med FeilResponse skal defineStatusWithError returnere riktig status og FeilResponse") {
                    val expectedFeilResponse =
                        FeilResponse(
                            type = "ingen-tilgang",
                            title = "Forbidden",
                            status = 403,
                            detail = "Ingen tilgang til ressurs",
                            instance = "/innkrevingsoppdrag/456",
                        )
                    val response = mockHttpResponse(403, body = expectedFeilResponse.encodeToString())
                    val (status, feilResponse) = defineStatusWithError(response.bodyAsText(), response.status)

                    status shouldBe Status.HTTP403_INGEN_TILGANG
                    feilResponse shouldBe expectedFeilResponse
                }

                test("Når response er 500 med FeilResponse skal defineStatusWithError returnere riktig status og FeilResponse") {
                    val expectedFeilResponse =
                        FeilResponse(
                            type = "intern-server-feil",
                            title = "Internal Server Error",
                            status = 500,
                            detail = "En intern feil har oppstått",
                            instance = "/innkrevingsoppdrag/789",
                        )
                    val response = mockHttpResponse(500, body = expectedFeilResponse.encodeToString())
                    val (status, feilResponse) = defineStatusWithError(response.bodyAsText(), response.status)

                    status shouldBe Status.HTTP500_INTERN_TJENERFEIL
                    feilResponse shouldBe expectedFeilResponse
                }

                test("Når response er 400 med UGYLDIG_KRAVIDENTIFIKATOR skal defineStatusWithError returnere riktig status og FeilResponse") {
                    val expectedFeilResponse =
                        FeilResponse(
                            type = UGYLDIG_KRAVIDENTIFIKATOR,
                            title = "Bad Request",
                            status = 400,
                            detail = "Ugyldig kravidentifikator",
                            instance = "/innkrevingsoppdrag/bad",
                        )
                    val response = mockHttpResponse(400, body = expectedFeilResponse.encodeToString())
                    val (status, feilResponse) = defineStatusWithError(response.bodyAsText(), response.status)

                    status shouldBe Status.HTTP400_UGYLDIG_KRAVIDENTIFIKATOR
                    feilResponse shouldBe expectedFeilResponse
                }

                test("Når response er 409 med KRAV_ER_IKKE_RESKONTROFOERT skal defineStatusWithError returnere riktig status og FeilResponse") {
                    val expectedFeilResponse =
                        FeilResponse(
                            type = KRAV_ER_IKKE_RESKONTROFOERT,
                            title = "Conflict",
                            status = 409,
                            detail = "Krav er ikke reskontroført",
                            instance = "/innkrevingsoppdrag/conflict",
                        )
                    val response = mockHttpResponse(409, body = expectedFeilResponse.encodeToString())
                    val (status, feilResponse) = defineStatusWithError(response.bodyAsText(), response.status)

                    status shouldBe Status.HTTP409_KRAV_ER_IKKE_RESKONTROFORT_RESEND
                    feilResponse shouldBe expectedFeilResponse
                }

                test("Når response er 404 uten FeilResponse (null) skal defineStatusWithError bruke default FEIL_FRA_SERVER type") {
                    val response = mockHttpResponse(404, body = "invalid json")
                    val (status, feilResponse) = defineStatusWithError(response.bodyAsText(), response.status)

                    status shouldBe Status.HTTP404_ANNEN_IKKE_FUNNET
                    feilResponse shouldBe null
                }

                test("Når response er 422 med FeilResponse skal defineStatusWithError returnere riktig status og FeilResponse") {
                    val expectedFeilResponse =
                        FeilResponse(
                            type = "valideringsfeil",
                            title = "Unprocessable Entity",
                            status = 422,
                            detail = "Valideringsfeil i request",
                            instance = "/innkrevingsoppdrag/validation",
                        )
                    val response = mockHttpResponse(422, body = expectedFeilResponse.encodeToString())
                    val (status, feilResponse) = defineStatusWithError(response.bodyAsText(), response.status)

                    status shouldBe Status.HTTP422_VALIDERINGSFEIL
                    feilResponse shouldBe expectedFeilResponse
                }

                test("Når response er 503 med FeilResponse skal defineStatusWithError returnere riktig status og FeilResponse") {
                    val expectedFeilResponse =
                        FeilResponse(
                            type = "tjeneste-utilgjengelig",
                            title = "Service Unavailable",
                            status = 503,
                            detail = "Tjenesten er midlertidig utilgjengelig",
                            instance = "/innkrevingsoppdrag/unavailable",
                        )
                    val response = mockHttpResponse(503, body = expectedFeilResponse.encodeToString())
                    val (status, feilResponse) = defineStatusWithError(response.bodyAsText(), response.status)
                    status shouldBe Status.HTTP503_UTILGJENGELIG_TJENESTE
                    feilResponse shouldBe expectedFeilResponse
                }

                test("Når response er 409 med AVSKREVET_KRAV_KAN_IKKE_ENDRES skal defineStatusWithError returnere riktig status og FeilResponse") {
                    val expectedFeilResponse =
                        FeilResponse(
                            type = AVSKREVET_KRAV_KAN_IKKE_ENDRES,
                            title = "Conflict",
                            status = 409,
                            detail = "Avskrevet krav kan ikke endres",
                            instance = "/innkrevingsoppdrag/avskrevet",
                        )
                    val response = mockHttpResponse(409, body = expectedFeilResponse.encodeToString())
                    val (status, feilResponse) = defineStatusWithError(response.bodyAsText(), response.status)

                    status shouldBe Status.HTTP409_AVSKREVET_KRAV_KAN_IKKE_ENDRES
                    feilResponse shouldBe expectedFeilResponse
                }

                test("Når response er 404 med KRAV_ER_IKKE_RESKONTROFOERT skal defineStatusWithError returnere riktig status og FeilResponse") {
                    val expectedFeilResponse =
                        FeilResponse(
                            type = KRAV_ER_IKKE_RESKONTROFOERT,
                            title = "Not Found",
                            status = 404,
                            detail = "Krav er ikke reskontroført",
                            instance = "/innkrevingsoppdrag/ikke-reskontrofort",
                        )
                    val response = mockHttpResponse(404, body = expectedFeilResponse.encodeToString())
                    val (status, feilResponse) = defineStatusWithError(response.bodyAsText(), response.status)

                    status shouldBe Status.HTTP404_KRAV_ER_IKKE_RESKONTROFORT
                    feilResponse shouldBe expectedFeilResponse
                }

                test("Når response er ukjent statuskode skal defineStatusWithError returnere UKJENT_FEIL og FeilResponse") {
                    val expectedFeilResponse =
                        FeilResponse(
                            type = "ukjent-feil",
                            title = "Unknown Error",
                            status = 999,
                            detail = "En ukjent feil har oppstått",
                            instance = "/innkrevingsoppdrag/unknown",
                        )
                    val response = mockHttpResponse(999, body = expectedFeilResponse.encodeToString())
                    val (status, feilResponse) = defineStatusWithError(response.bodyAsText(), response.status)

                    status shouldBe Status.UKJENT_FEIL
                    feilResponse shouldBe expectedFeilResponse
                }
            }
        },
    )
