package sokos.ske.krav.domain

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import sokos.ske.krav.database.models.KravTable
import sokos.ske.krav.util.*


internal class DefineStatusTest : FunSpec({

    test("Når responsekode er 400 skal krav ha status Status.UGYLDIG_FORESPORSEL_400") {
        val requestResult = RequestResult(mockHttpResponse(400), mockk<KravTable>(), "", "")
        requestResult.status shouldBe Status.HTTP400_UGYLDIG_FORESPORSEL
    }

    test("Når responsekode er 401 skal krav ha status Status.FEIL_AUTENTISERING_401") {
        val requestResult = RequestResult(mockHttpResponse(401), mockk<KravTable>(), "", "")
        requestResult.status shouldBe Status.HTTP401_FEIL_AUTENTISERING
    }


    test("Når responsekode er 403 skal krav ha status Status.INGEN_TILGANG_403") {
        val requestResult = RequestResult(mockHttpResponse(403), mockk<KravTable>(), "", "")
        requestResult.status shouldBe Status.HTTP403_INGEN_TILGANG
    }

    test("Når responsekode er 404 og typen inneholder KRAV_EKSISTERER_IKKE, skal krav ha status Status.FANT_IKKE_SAKSREF_404") {
        val requestResult =
            RequestResult(mockHttpResponse(404, "test $KRAV_EKSISTERER_IKKE"), mockk<KravTable>(), "", "")
        requestResult.status shouldBe Status.HTTP404_FANT_IKKE_SAKSREF
    }

    test("Når responsekode er 404 og typen ikke gjenkjennes, skal krav ha status Status.ANNEN_IKKE_FUNNET_404") {
        val requestResult = RequestResult(mockHttpResponse(404), mockk<KravTable>(), "", "")
        requestResult.status shouldBe Status.HTTP404_ANNEN_IKKE_FUNNET
    }

    test("Når responsekode er 406 skal krav ha status Status.FEIL_MEDIETYPE_406") {
        val requestResult = RequestResult(mockHttpResponse(406), mockk<KravTable>(), "", "")
        requestResult.status shouldBe Status.HTTP406_FEIL_MEDIETYPE
    }

    test("Når responsekode er 409 og typen inneholder KRAV_IKKE_RESKONTROFORT_RESEND skal krav ha status Status.IKKE_RESKONTROFORT_RESEND") {
        val requestResult =
            RequestResult(mockHttpResponse(409, "test $KRAV_IKKE_RESKONTROFORT_RESEND"), mockk<KravTable>(), "", "")
        requestResult.status shouldBe Status.HTTP409_IKKE_RESKONTROFORT_RESEND
    }

    test("Når responsekode er 409 og typen inneholder KRAV_ER_AVSKREVET eller KRAV_ER_ALLEREDE_AVSKREVET skal krav ha status Status.KRAV_ER_AVSKREVET_409") {
        val requestResultAvskrevet =
            RequestResult(mockHttpResponse(409, "test $KRAV_ER_AVSKREVET"), mockk<KravTable>(), "", "")
        requestResultAvskrevet.status shouldBe Status.HTTP409_KRAV_ER_AVSKREVET
        val requestResultAlleredeAvskrevet =
            RequestResult(mockHttpResponse(409, "test $KRAV_ER_ALLEREDE_AVSKREVET"), mockk<KravTable>(), "", "")
        requestResultAlleredeAvskrevet.status shouldBe Status.HTTP409_KRAV_ER_AVSKREVET
    }

    test("Når responsekode er 409 og typen ikke gjenkjennes skal krav ha status Status.ANNEN_KONFLIKT_409") {
        val requestResult = RequestResult(mockHttpResponse(409), mockk<KravTable>(), "", "")
        requestResult.status shouldBe Status.HTTP409_ANNEN_KONFLIKT
    }
    test("Når responsekode er 422 skal krav ha status Status.VALIDERINGSFEIL_422") {
        val requestResult = RequestResult(mockHttpResponse(422), mockk<KravTable>(), "", "")
        requestResult.status shouldBe Status.HTTP422_VALIDERINGSFEIL
    }
    test("Når responsekode er 500 skal krav ha status Status.INTERN_TJENERFEIL_500") {
        val requestResult = RequestResult(mockHttpResponse(500), mockk<KravTable>(), "", "")
        requestResult.status shouldBe Status.HTTP500_INTERN_TJENERFEIL
    }

    test("Når responsekode er 503 skal krav ha status Status.UTILGJENGELIG_TJENESTE_503") {
        val requestResult = RequestResult(mockHttpResponse(503), mockk<KravTable>(), "", "")
        requestResult.status shouldBe Status.HTTP503_UTILGJENGELIG_TJENESTE
    }

    test("Når responsekode er i 300-serien og ikke spesifikt dekket så skal krav ha status Status.REDIRECTION_FEIL_300"){
        val requestResult = RequestResult(mockHttpResponse(301), mockk<KravTable>(), "", "")
        requestResult.status shouldBe Status.HTTP300_REDIRECTION_FEIL
    }
    
    test("Når responsekode er i 400-serien og ikke spesifikt dekket så skal krav ha status Status.ANNEN_KLIENT_FEIL_400") {
        val requestResult = RequestResult(mockHttpResponse(420), mockk<KravTable>(), "", "")
        requestResult.status shouldBe Status.HTTP400_ANNEN_KLIENT_FEIL
    }

    test("Når responsekode er i 500-serien og ikke spesifikt dekket så skal krav ha status Status.ANNEN_SERVER_FEIL_500"){
        val requestResult = RequestResult(mockHttpResponse(502), mockk<KravTable>(), "", "")
        requestResult.status shouldBe Status.HTTP500_ANNEN_SERVER_FEIL
    }
    test("Når responsekode ikke er dekket så skal krav ha status Status.UKJENT_FEIL"){
        val requestResult = RequestResult(mockHttpResponse(102), mockk<KravTable>(), "", "")
        requestResult.status shouldBe Status.UKJENT_FEIL
    }


}


)