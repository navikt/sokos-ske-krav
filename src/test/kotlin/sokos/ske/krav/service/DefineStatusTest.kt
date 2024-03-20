package sokos.ske.krav.service

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import sokos.ske.krav.database.models.Status
import sokos.ske.krav.util.*

internal class DefineStatusTest : FunSpec({

    test("Når responsekode er 400 skal krav ha status Status.UGYLDIG_FORESPORSEL_400") {
        defineStatus(mockFeilResponsCall(400))  shouldBe Status.UGYLDIG_FORESPORSEL_400
    }

    test("Når responsekode er 401 skal krav ha status Status.FEIL_AUTENTISERING_401") {
        defineStatus(mockFeilResponsCall(401)) shouldBe Status.FEIL_AUTENTISERING_401
    }

    test("Når responsekode er 403 skal krav ha status Status.INGEN_TILGANG_403") {
        defineStatus(mockFeilResponsCall(403)) shouldBe Status.INGEN_TILGANG_403
    }

    test("Når responsekode er 404 og typen inneholder KRAV_EKSISTERER_IKKE, skal krav ha status Status.FANT_IKKE_SAKSREF_404") {
        defineStatus(mockFeilResponsCall(404,"test $KRAV_EKSISTERER_IKKE"))  shouldBe Status.FANT_IKKE_SAKSREF_404
    }

    test("Når responsekode er 404 og typen ikke gjenkjennes, skal krav ha status Status.ANNEN_IKKE_FUNNET_404") {
        defineStatus(mockFeilResponsCall(404))  shouldBe Status.ANNEN_IKKE_FUNNET_404
    }

    test("Når responsekode er 406 skal krav ha status Status.FEIL_MEDIETYPE_406") {
        defineStatus(mockFeilResponsCall(406)) shouldBe Status.FEIL_MEDIETYPE_406
    }

    test("Når responsekode er 409 og typen inneholder KRAV_IKKE_RESKONTROFORT_RESEND skal krav ha status Status.IKKE_RESKONTROFORT_RESEND") {
        defineStatus(mockFeilResponsCall(409, "test $KRAV_IKKE_RESKONTROFORT_RESEND" )) shouldBe Status.IKKE_RESKONTROFORT_RESEND
    }

    test("Når responsekode er 409 og typen inneholder KRAV_ER_AVSKREVET eller KRAV_ER_ALLEREDE_AVSKREVET skal krav ha status Status.KRAV_ER_AVSKREVET_409") {
        defineStatus(mockFeilResponsCall(409, "test $KRAV_ER_AVSKREVET" )) shouldBe Status.KRAV_ER_AVSKREVET_409
        defineStatus(mockFeilResponsCall(409, "test $KRAV_ER_ALLEREDE_AVSKREVET" )) shouldBe Status.KRAV_ER_AVSKREVET_409
    }

    test("Når responsekode er 409 og typen ikke gjenkjennes skal krav ha status Status.ANNEN_KONFLIKT_409") {
        defineStatus(mockFeilResponsCall(409)) shouldBe Status.ANNEN_KONFLIKT_409
    }
    test("Når responsekode er 422 skal krav ha status Status.VALIDERINGSFEIL_422") {
        defineStatus(mockFeilResponsCall(422)) shouldBe Status.VALIDERINGSFEIL_422
    }
    test("Når responsekode er 500 skal krav ha status Status.INTERN_TJENERFEIL_500") {
        defineStatus(mockFeilResponsCall(500)) shouldBe Status.INTERN_TJENERFEIL_500
    }
    test("Når responsekode er 503 skal krav ha status Status.UTILGJENGELIG_TJENESTE_503") {
        defineStatus(mockFeilResponsCall(503)) shouldBe Status.UTILGJENGELIG_TJENESTE_503
    }
    test("Når responsekode ikke gjenkjennes skal krav ha status Status.ANNEN_KLIENT_FEIL_400") {
        defineStatus(mockFeilResponsCall(420)) shouldBe Status.ANNEN_KLIENT_FEIL_400
    }


}



)