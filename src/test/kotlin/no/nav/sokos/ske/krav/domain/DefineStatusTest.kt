package no.nav.sokos.ske.krav.domain

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode

import no.nav.sokos.ske.krav.dto.ske.responses.FeilResponse
import no.nav.sokos.ske.krav.util.AVSKREVET_KRAV_KAN_IKKE_AVSKRIVES
import no.nav.sokos.ske.krav.util.AVSKREVET_KRAV_KAN_IKKE_ENDRES
import no.nav.sokos.ske.krav.util.KRAV_EKSISTERER_IKKE
import no.nav.sokos.ske.krav.util.KRAV_ER_ALLEREDE_AVSKREVET
import no.nav.sokos.ske.krav.util.KRAV_ER_AVSKREVET
import no.nav.sokos.ske.krav.util.KRAV_ER_IKKE_RESKONTROFOERT
import no.nav.sokos.ske.krav.util.OPPDRAGSGIVERS_KRAVIDENTIFIKATOR_EKSISTERER
import no.nav.sokos.ske.krav.util.UGYLDIG_KRAVIDENTIFIKATOR
import no.nav.sokos.ske.krav.util.UGYLDIG_TILLEGGSINFORMASJON
import no.nav.sokos.ske.krav.util.defineStatus
import no.nav.sokos.ske.krav.util.encodeToString

internal class DefineStatusTest :
    FunSpec(
        {

            fun createFeilResponse(
                type: String,
                status: Int,
            ): FeilResponse = FeilResponse(type, "tittel", status, "detaljer", "enurl")

            test("Når response er success (200) skal defineStatus returnere KRAV_SENDT og null FeilResponse") {
                val (status, feilResponse) = defineStatus("", HttpStatusCode.OK)

                status shouldBe Status.KRAV_SENDT
                feilResponse shouldBe null
            }
            test("Når response er ukjent statuskode skal defineStatus returnere UKJENT_FEIL og FeilResponse") {
                val expectedFeilResponse = createFeilResponse("ukjent-feil", 999)
                val (status, feilResponse) = defineStatus(expectedFeilResponse.encodeToString(), HttpStatusCode.fromValue(999))

                status shouldBe Status.UKJENT_FEIL
                feilResponse shouldBe expectedFeilResponse
            }
            test("Når response er 404 med ikke-parsebar body skal defineStatus lage en custom fallback FeilResponse med FEIL_FRA_SERVER type") {
                val (status, feilResponse) = defineStatus("invalid json", HttpStatusCode.NotFound)

                status shouldBe Status.HTTP404_ANNEN_IKKE_FUNNET
                feilResponse shouldBe
                    FeilResponse(
                        type = FeilResponse.CustomTypes.FEIL_FRA_SERVER,
                        title = "Feil fra SKE",
                        status = 404,
                        detail = "invalid json",
                        instance = "",
                    )
            }

            test("Når response er 500 med ikke-parsebar body på mer enn 500 tegn skal detail truncate og inkludere total lengde") {
                val longBody = "x".repeat(600)
                val (status, feilResponse) = defineStatus(longBody, HttpStatusCode.InternalServerError)

                status shouldBe Status.HTTP500_INTERN_TJENERFEIL
                feilResponse shouldBe
                    FeilResponse(
                        type = FeilResponse.CustomTypes.FEIL_FRA_SERVER,
                        title = "Feil fra SKE",
                        status = 500,
                        detail = "${"x".repeat(500)}... [600 tegn totalt]",
                        instance = "",
                    )
            }

            test("Når responsekode er 400 skal krav ha status Status.UGYLDIG_FORESPORSEL_400") {
                val expectedFeilResponse = createFeilResponse("ugyldig-foresporsel", 400)
                val (status, feilResponse) = defineStatus(expectedFeilResponse.encodeToString(), HttpStatusCode.BadRequest)

                status shouldBe Status.HTTP400_UGYLDIG_FORESPORSEL
                feilResponse shouldBe expectedFeilResponse
            }

            test("Når responsekode er 400 og typen inneholder UGYLDIG_KRAVIDENTIFIKATOR skal krav ha status Status.HTTP400_UGYLDIG_KRAVIDENTIFIKATOR") {
                val expectedFeilResponse = createFeilResponse(UGYLDIG_KRAVIDENTIFIKATOR, 400)
                val (status, feilResponse) = defineStatus(expectedFeilResponse.encodeToString(), HttpStatusCode.BadRequest)

                status shouldBe Status.HTTP400_UGYLDIG_KRAVIDENTIFIKATOR
                feilResponse shouldBe expectedFeilResponse
            }

            test("Når responsekode er 400 og typen inneholder UGYLDIG_TILLEGGSINFORMASJON skal krav ha status Status.HTTP400_UGYLDIG_TILLEGGSINFORMASJON") {
                val expectedFeilResponse = createFeilResponse(UGYLDIG_TILLEGGSINFORMASJON, 400)
                val (status, feilResponse) = defineStatus(expectedFeilResponse.encodeToString(), HttpStatusCode.BadRequest)

                status shouldBe Status.HTTP400_UGYLDIG_TILLEGGSINFORMASJON
                feilResponse shouldBe expectedFeilResponse
            }

            test("Når responsekode er 401 skal krav ha status Status.FEIL_AUTENTISERING_401") {
                val expectedFeilResponse = createFeilResponse("feil-auth", 401)
                val (status, feilResponse) = defineStatus(expectedFeilResponse.encodeToString(), HttpStatusCode.Unauthorized)

                status shouldBe Status.HTTP401_FEIL_AUTENTISERING
                feilResponse shouldBe expectedFeilResponse
            }

            test("Når responsekode er 403 skal krav ha status Status.INGEN_TILGANG_403") {
                val expectedFeilResponse = createFeilResponse("forbiddem", 403)
                val (status, feilResponse) = defineStatus(expectedFeilResponse.encodeToString(), HttpStatusCode.Forbidden)

                status shouldBe Status.HTTP403_INGEN_TILGANG
                feilResponse shouldBe expectedFeilResponse
            }

            test("Når responsekode er 404 og typen inneholder KRAV_EKSISTERER_IKKE, skal krav ha status Status.FANT_IKKE_SAKSREF_404") {
                val expectedFeilResponse = createFeilResponse(KRAV_EKSISTERER_IKKE, 404)
                val (status, feilResponse) = defineStatus(expectedFeilResponse.encodeToString(), HttpStatusCode.NotFound)

                status shouldBe Status.HTTP404_FANT_IKKE_SAKSREF
                feilResponse shouldBe expectedFeilResponse
            }

            test("Når responsekode er 404 og typen ikke gjenkjennes, skal krav ha status Status.ANNEN_IKKE_FUNNET_404") {
                val expectedFeilResponse = createFeilResponse("foo", 404)
                val (status, feilResponse) = defineStatus(expectedFeilResponse.encodeToString(), HttpStatusCode.NotFound)

                status shouldBe Status.HTTP404_ANNEN_IKKE_FUNNET
                feilResponse shouldBe expectedFeilResponse
            }

            test("Når responsekode er 404 og typen inneholder KRAV_ER_IKKE_RESKONTROFOERT skal krav ha status Status.HTTP404_KRAV_ER_IKKE_RESKONTROFORT") {
                val expectedFeilResponse = createFeilResponse(KRAV_ER_IKKE_RESKONTROFOERT, 404)
                val (status, feilResponse) = defineStatus(expectedFeilResponse.encodeToString(), HttpStatusCode.NotFound)

                status shouldBe Status.HTTP404_KRAV_ER_IKKE_RESKONTROFORT
                feilResponse shouldBe expectedFeilResponse
            }

            test("Når responsekode er 406 skal krav ha status Status.HTTP406_FEIL_MEDIETYPE") {
                val expectedFeilResponse = createFeilResponse("not-acceptable", 406)
                val (status, feilResponse) = defineStatus(expectedFeilResponse.encodeToString(), HttpStatusCode.NotAcceptable)

                status shouldBe Status.HTTP406_FEIL_MEDIETYPE
                feilResponse shouldBe expectedFeilResponse
            }

            test("Når responsekode er 409 og typen inneholder KRAV_ER_AVSKREVET skal krav ha status Status.KRAV_ER_AVSKREVET_409") {
                val expectedFeilResponse = createFeilResponse(KRAV_ER_AVSKREVET, 409)
                val (status, feilResponse) = defineStatus(expectedFeilResponse.encodeToString(), HttpStatusCode.Conflict)

                status shouldBe Status.HTTP409_KRAV_ER_AVSKREVET
                feilResponse shouldBe expectedFeilResponse
            }

            test("Når responsekode er 409 og typen inneholder KRAV_ER_ALLEREDE_AVSKREVET skal krav ha status Status.KRAV_ER_AVSKREVET_409") {
                val expectedFeilResponse = createFeilResponse(KRAV_ER_ALLEREDE_AVSKREVET, 409)
                val (status, feilResponse) = defineStatus(expectedFeilResponse.encodeToString(), HttpStatusCode.Conflict)

                status shouldBe Status.HTTP409_KRAV_ER_AVSKREVET
                feilResponse shouldBe expectedFeilResponse
            }

            test("Når responsekode er 409 og typen inneholder AVSKREVET_KRAV_KAN_IKKE_ENDRES skal krav ha status Status.HTTP409_AVSKREVET_KRAV_KAN_IKKE_ENDRES") {
                val expectedFeilResponse = createFeilResponse(AVSKREVET_KRAV_KAN_IKKE_ENDRES, 409)
                val (status, feilResponse) = defineStatus(expectedFeilResponse.encodeToString(), HttpStatusCode.Conflict)

                status shouldBe Status.HTTP409_AVSKREVET_KRAV_KAN_IKKE_ENDRES
                feilResponse shouldBe expectedFeilResponse
            }

            test("Når responsekode er 409 og typen inneholder AVSKREVET_KRAV_KAN_IKKE_AVSKRIVES skal krav ha status Status.HTTP409_AVSKREVET_KRAV_KAN_IKKE_AVSKRIVES") {
                val expectedFeilResponse = createFeilResponse(AVSKREVET_KRAV_KAN_IKKE_AVSKRIVES, 409)
                val (status, feilResponse) = defineStatus(expectedFeilResponse.encodeToString(), HttpStatusCode.Conflict)

                status shouldBe Status.HTTP409_AVSKREVET_KRAV_KAN_IKKE_AVSKRIVES
                feilResponse shouldBe expectedFeilResponse
            }

            test("Når responsekode er 409 og typen inneholder OPPDRAGSGIVERS_KRAVIDENTIFIKATOR_EKSISTERER skal krav ha status Status.HTTP409_KRAVIDENTIFIKATOR_EKSISTERER") {
                val expectedFeilResponse = createFeilResponse(OPPDRAGSGIVERS_KRAVIDENTIFIKATOR_EKSISTERER, 409)
                val (status, feilResponse) = defineStatus(expectedFeilResponse.encodeToString(), HttpStatusCode.Conflict)

                status shouldBe Status.HTTP409_KRAVIDENTIFIKATOR_EKSISTERER
                feilResponse shouldBe expectedFeilResponse
            }

            test("Når responsekode er 409 og typen inneholder KRAV_ER_IKKE_RESKONTROFOERT skal krav ha status Status.HTTP409_KRAV_ER_IKKE_RESKONTROFORT_RESEND") {
                val expectedFeilResponse = createFeilResponse(KRAV_ER_IKKE_RESKONTROFOERT, 409)
                val (status, feilResponse) = defineStatus(expectedFeilResponse.encodeToString(), HttpStatusCode.Conflict)

                status shouldBe Status.HTTP409_KRAV_ER_IKKE_RESKONTROFORT_RESEND
                feilResponse shouldBe expectedFeilResponse
            }

            test("Når responsekode er 409 og typen ikke gjenkjennes skal krav ha status Status.ANNEN_KONFLIKT_409") {
                val expectedFeilResponse = createFeilResponse("annen-konflikt", 409)
                val (status, feilResponse) = defineStatus(expectedFeilResponse.encodeToString(), HttpStatusCode.Conflict)

                status shouldBe Status.HTTP409_ANNEN_KONFLIKT
                feilResponse shouldBe expectedFeilResponse
            }

            test("Når responsekode er 422 skal krav ha status Status.VALIDERINGSFEIL_422") {
                val expectedFeilResponse = createFeilResponse("valideringsfeil", 422)
                val (status, feilResponse) = defineStatus(expectedFeilResponse.encodeToString(), HttpStatusCode.UnprocessableEntity)

                status shouldBe Status.HTTP422_VALIDERINGSFEIL
                feilResponse shouldBe expectedFeilResponse
            }

            test("Når responsekode er 500 skal krav ha status Status.INTERN_TJENERFEIL_500") {
                val expectedFeilResponse = createFeilResponse("intern-tjenerfeil", 500)
                val (status, feilResponse) = defineStatus(expectedFeilResponse.encodeToString(), HttpStatusCode.InternalServerError)

                status shouldBe Status.HTTP500_INTERN_TJENERFEIL
                feilResponse shouldBe expectedFeilResponse
            }

            test("Når responsekode er 503 skal krav ha status Status.UTILGJENGELIG_TJENESTE_503") {
                val expectedFeilResponse = createFeilResponse("utilgjengelig-tjeneste", 503)
                val (status, feilResponse) = defineStatus(expectedFeilResponse.encodeToString(), HttpStatusCode.ServiceUnavailable)

                status shouldBe Status.HTTP503_UTILGJENGELIG_TJENESTE
                feilResponse shouldBe expectedFeilResponse
            }

            test("Når responsekode er i 300-serien og ikke spesifikt dekket så skal krav ha status Status.REDIRECTION_FEIL_300") {
                val expectedFeilResponse = createFeilResponse("redirection", 301)
                val (status, feilResponse) = defineStatus(expectedFeilResponse.encodeToString(), HttpStatusCode.MovedPermanently)

                status shouldBe Status.HTTP300_REDIRECTION_FEIL
                feilResponse shouldBe expectedFeilResponse
            }

            test("Når responsekode er i 400-serien og ikke spesifikt dekket så skal krav ha status Status.ANNEN_KLIENT_FEIL_400") {
                val expectedFeilResponse = createFeilResponse("klient-feil", 420)
                val (status, feilResponse) = defineStatus(expectedFeilResponse.encodeToString(), HttpStatusCode.fromValue(420))

                status shouldBe Status.HTTP400_ANNEN_KLIENT_FEIL
                feilResponse shouldBe expectedFeilResponse
            }

            test("Når responsekode er i 500-serien og ikke spesifikt dekket så skal krav ha status Status.ANNEN_SERVER_FEIL_500") {
                val expectedFeilResponse = createFeilResponse("server-feil", 502)
                val (status, feilResponse) = defineStatus(expectedFeilResponse.encodeToString(), HttpStatusCode.BadGateway)

                status shouldBe Status.HTTP500_ANNEN_SERVER_FEIL
                feilResponse shouldBe expectedFeilResponse
            }
        },
    )
