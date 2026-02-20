package no.nav.sokos.ske.krav.util

import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess

import no.nav.sokos.ske.krav.domain.Krav
import no.nav.sokos.ske.krav.domain.Status
import no.nav.sokos.ske.krav.dto.ske.responses.FeilResponse

const val KRAV_ER_AVSKREVET = "innkrevingsoppdrag-er-avskrevet"
const val KRAV_ER_ALLEREDE_AVSKREVET = "innkrevingsoppdrag-er-allerede-avskrevet"
const val KRAV_EKSISTERER_IKKE = "innkrevingsoppdrag-eksisterer-ikke"
const val AVSKREVET_KRAV_KAN_IKKE_ENDRES = "avskrevet-innkrevingsoppdrag-kan-ikke-endres"
const val AVSKREVET_KRAV_KAN_IKKE_AVSKRIVES = "avskrevet-innkrevingsoppdrag-kan-ikke-avskrives"
const val OPPDRAGSGIVERS_KRAVIDENTIFIKATOR_EKSISTERER = "oppdragsgivers-kravidentifikator-eksisterer-allerede"
const val UGYLDIG_KRAVIDENTIFIKATOR = "ugyldig-kravidentifikator"
const val UGYLDIG_TILLEGGSINFORMASJON = "ugyldig-tilleggsinformasjon"
const val KRAV_ER_IKKE_RESKONTROFOERT = "innkrevingsoppdrag-er-ikke-reskontrofoert"

data class RequestResult(
    val response: HttpResponse,
    val krav: Krav,
    val request: String,
    val kravidentifikator: String,
    val status: Status,
    val feilResponse: FeilResponse? = null,
)

suspend fun defineStatus(
    responseBody: String,
    httpStatus: HttpStatusCode,
) = defineStatusWithError(responseBody, httpStatus).first

suspend fun defineStatusWithError(
    responseBody: String,
    httpStatus: HttpStatusCode,
): Pair<Status, FeilResponse?> {
    println("RESPONSE BODY: $responseBody")
    if (httpStatus.isSuccess()) return Pair(Status.KRAV_SENDT, null)
    val feilResponse = responseBody.decodeTo<FeilResponse>()
    val errorType = feilResponse?.type ?: FeilResponse.CustomTypes.FEIL_FRA_SERVER

    val status =
        when (httpStatus.value) {
            400 -> handleBadRequestError(errorType)
            401 -> Status.HTTP401_FEIL_AUTENTISERING
            403 -> Status.HTTP403_INGEN_TILGANG
            404 -> handleNotFoundError(errorType)
            406 -> Status.HTTP406_FEIL_MEDIETYPE
            409 -> handleConflictError(errorType)
            422 -> Status.HTTP422_VALIDERINGSFEIL
            500 -> Status.HTTP500_INTERN_TJENERFEIL
            503 -> Status.HTTP503_UTILGJENGELIG_TJENESTE
            in 300..399 -> Status.HTTP300_REDIRECTION_FEIL
            in 400..499 -> Status.HTTP400_ANNEN_KLIENT_FEIL
            in 500..599 -> Status.HTTP500_ANNEN_SERVER_FEIL
            else -> Status.UKJENT_FEIL
        }

    return Pair(status, feilResponse)
}

private fun handleBadRequestError(errorType: String): Status =
    when {
        errorType.contains(UGYLDIG_KRAVIDENTIFIKATOR) -> Status.HTTP400_UGYLDIG_KRAVIDENTIFIKATOR
        errorType.contains(UGYLDIG_TILLEGGSINFORMASJON) -> Status.HTTP400_UGYLDIG_TILLEGGSINFORMASJON
        else -> Status.HTTP400_UGYLDIG_FORESPORSEL
    }

private fun handleNotFoundError(errorType: String): Status =
    when {
        errorType.contains(KRAV_EKSISTERER_IKKE) -> Status.HTTP404_FANT_IKKE_SAKSREF
        errorType.contains(KRAV_ER_IKKE_RESKONTROFOERT) -> Status.HTTP404_KRAV_ER_IKKE_RESKONTROFORT
        else -> Status.HTTP404_ANNEN_IKKE_FUNNET
    }

private fun handleConflictError(errorType: String): Status =
    when {
        errorType.contains(KRAV_ER_IKKE_RESKONTROFOERT) -> Status.HTTP409_KRAV_ER_IKKE_RESKONTROFORT_RESEND
        errorType.contains(AVSKREVET_KRAV_KAN_IKKE_ENDRES) -> Status.HTTP409_AVSKREVET_KRAV_KAN_IKKE_ENDRES
        errorType.contains(AVSKREVET_KRAV_KAN_IKKE_AVSKRIVES) -> Status.HTTP409_AVSKREVET_KRAV_KAN_IKKE_AVSKRIVES
        errorType.contains(KRAV_ER_AVSKREVET) || errorType.contains(KRAV_ER_ALLEREDE_AVSKREVET) -> Status.HTTP409_KRAV_ER_AVSKREVET
        errorType.contains(OPPDRAGSGIVERS_KRAVIDENTIFIKATOR_EKSISTERER) -> Status.HTTP409_KRAVIDENTIFIKATOR_EKSISTERER
        else -> Status.HTTP409_ANNEN_KONFLIKT
    }
