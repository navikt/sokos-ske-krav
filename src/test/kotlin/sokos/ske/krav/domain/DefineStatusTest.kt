package sokos.ske.krav.domain

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import sokos.ske.krav.util.KRAV_EKSISTERER_IKKE
import sokos.ske.krav.util.KRAV_ER_ALLEREDE_AVSKREVET
import sokos.ske.krav.util.KRAV_ER_AVSKREVET
import sokos.ske.krav.util.KRAV_IKKE_RESKONTROFORT_RESEND
import sokos.ske.krav.util.RequestResult
import sokos.ske.krav.util.defineStatus
import sokos.ske.krav.util.mockHttpResponse

internal class DefineStatusTest :
    FunSpec(
        {
            suspend fun createRequestResult(
                responseCode: Int,
                responseBody: String = "",
            ): RequestResult {
                val response = mockHttpResponse(responseCode, responseBody)
                return RequestResult(response, mockk(), "", "", defineStatus(response))
            }

            test("Når responsekode er 400 skal krav ha status Status.UGYLDIG_FORESPORSEL_400") {
                createRequestResult(400).status shouldBe Status.HTTP400_UGYLDIG_FORESPORSEL
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

            test("Når responsekode er 406 skal krav ha status Status.FEIL_MEDIETYPE_406") {
                createRequestResult(406).status shouldBe Status.HTTP406_FEIL_MEDIETYPE
            }

            test("Når responsekode er 409 og typen inneholder KRAV_IKKE_RESKONTROFORT_RESEND skal krav ha status Status.IKKE_RESKONTROFORT_RESEND") {
                createRequestResult(409, "test $KRAV_IKKE_RESKONTROFORT_RESEND").status shouldBe Status.HTTP409_IKKE_RESKONTROFORT_RESEND
            }

            test("Når responsekode er 409 og typen inneholder KRAV_ER_AVSKREVET eller KRAV_ER_ALLEREDE_AVSKREVET skal krav ha status Status.KRAV_ER_AVSKREVET_409") {
                createRequestResult(409, "test $KRAV_ER_AVSKREVET").status shouldBe Status.HTTP409_KRAV_ER_AVSKREVET
                createRequestResult(409, "test $KRAV_ER_ALLEREDE_AVSKREVET").status shouldBe Status.HTTP409_KRAV_ER_AVSKREVET
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
        },
    )
