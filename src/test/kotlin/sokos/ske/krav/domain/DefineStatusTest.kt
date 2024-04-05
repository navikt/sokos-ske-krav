package sokos.ske.krav.domain

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import sokos.ske.krav.database.models.KravTable
import sokos.ske.krav.util.*


internal class DefineStatusTest : FunSpec({

    test("Når responsekode er 400 skal krav ha status Status.UGYLDIG_FORESPORSEL_400") {
        val requestResult = RequestResult(mockHttpResponse(400), mockk<KravTable>(), "", "")
        requestResult.status shouldBe Status.UGYLDIG_FORESPORSEL_400
    }

    test("Når responsekode er 401 skal krav ha status Status.FEIL_AUTENTISERING_401") {
        val requestResult = RequestResult(mockHttpResponse(401), mockk<KravTable>(), "", "")
        requestResult.status shouldBe Status.FEIL_AUTENTISERING_401
    }


    test("Når responsekode er 403 skal krav ha status Status.INGEN_TILGANG_403") {
        val requestResult = RequestResult(mockHttpResponse(403), mockk<KravTable>(), "", "")
        requestResult.status shouldBe Status.INGEN_TILGANG_403
    }

    test("Når responsekode er 404 og typen inneholder KRAV_EKSISTERER_IKKE, skal krav ha status Status.FANT_IKKE_SAKSREF_404") {
        val requestResult =
            RequestResult(mockHttpResponse(404, "test $KRAV_EKSISTERER_IKKE"), mockk<KravTable>(), "", "")
        requestResult.status shouldBe Status.FANT_IKKE_SAKSREF_404
    }

    test("Når responsekode er 404 og typen ikke gjenkjennes, skal krav ha status Status.ANNEN_IKKE_FUNNET_404") {
        val requestResult = RequestResult(mockHttpResponse(404), mockk<KravTable>(), "", "")
        requestResult.status shouldBe Status.ANNEN_IKKE_FUNNET_404
    }

    test("Når responsekode er 406 skal krav ha status Status.FEIL_MEDIETYPE_406") {
        val requestResult = RequestResult(mockHttpResponse(406), mockk<KravTable>(), "", "")
        requestResult.status shouldBe Status.FEIL_MEDIETYPE_406
    }

    test("Når responsekode er 409 og typen inneholder KRAV_IKKE_RESKONTROFORT_RESEND skal krav ha status Status.IKKE_RESKONTROFORT_RESEND") {
        val requestResult =
            RequestResult(mockHttpResponse(409, "test $KRAV_IKKE_RESKONTROFORT_RESEND"), mockk<KravTable>(), "", "")
        requestResult.status shouldBe Status.IKKE_RESKONTROFORT_RESEND
    }

    test("Når responsekode er 409 og typen inneholder KRAV_ER_AVSKREVET eller KRAV_ER_ALLEREDE_AVSKREVET skal krav ha status Status.KRAV_ER_AVSKREVET_409") {
        val requestResultAvskrevet =
            RequestResult(mockHttpResponse(409, "test $KRAV_ER_AVSKREVET"), mockk<KravTable>(), "", "")
        requestResultAvskrevet.status shouldBe Status.KRAV_ER_AVSKREVET_409
        val requestResultAlleredeAvskrevet =
            RequestResult(mockHttpResponse(409, "test $KRAV_ER_ALLEREDE_AVSKREVET"), mockk<KravTable>(), "", "")
        requestResultAlleredeAvskrevet.status shouldBe Status.KRAV_ER_AVSKREVET_409
    }

    test("Når responsekode er 409 og typen ikke gjenkjennes skal krav ha status Status.ANNEN_KONFLIKT_409") {
        val requestResult = RequestResult(mockHttpResponse(409), mockk<KravTable>(), "", "")
        requestResult.status shouldBe Status.ANNEN_KONFLIKT_409
    }
    test("Når responsekode er 422 skal krav ha status Status.VALIDERINGSFEIL_422") {
        val requestResult = RequestResult(mockHttpResponse(422), mockk<KravTable>(), "", "")
        requestResult.status shouldBe Status.VALIDERINGSFEIL_422
    }
    test("Når responsekode er 500 skal krav ha status Status.INTERN_TJENERFEIL_500") {
        val requestResult = RequestResult(mockHttpResponse(500), mockk<KravTable>(), "", "")
        requestResult.status shouldBe Status.INTERN_TJENERFEIL_500
    }
    test("Når responsekode er 503 skal krav ha status Status.UTILGJENGELIG_TJENESTE_503") {
        val requestResult = RequestResult(mockHttpResponse(503), mockk<KravTable>(), "", "")
        requestResult.status shouldBe Status.UTILGJENGELIG_TJENESTE_503
    }
    test("Når responsekode ikke gjenkjennes skal krav ha status Status.ANNEN_KLIENT_FEIL_400") {
        val requestResult = RequestResult(mockHttpResponse(420), mockk<KravTable>(), "", "")
        requestResult.status shouldBe Status.ANNEN_KLIENT_FEIL_400
    }


}


)