package sokos.ske.krav.util

import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess
import sokos.ske.krav.database.models.KravTable
import sokos.ske.krav.domain.Status
import sokos.ske.krav.domain.ske.responses.FeilResponse

const val KRAV_IKKE_RESKONTROFORT_RESEND = "innkrevingsoppdrag-er-ikke-reskontrofoert"
const val KRAV_ER_AVSKREVET = "innkrevingsoppdrag-er-avskrevet"
const val KRAV_ER_ALLEREDE_AVSKREVET = "innkrevingsoppdrag-er-allerede-avskrevet"
const val KRAV_EKSISTERER_IKKE = "innkrevingsoppdrag-eksisterer-ikke"

data class RequestResult(
    val response: HttpResponse,
    val kravTable: KravTable,
    val request: String,
    val kravidentifikator: String,
    val status: Status,
)

suspend fun defineStatus(response: HttpResponse): Status {
            if (response.status.isSuccess()) return Status.KRAV_SENDT
    val content = response.body<FeilResponse>()

            return when (response.status.value) {
                400 -> Status.HTTP400_UGYLDIG_FORESPORSEL
                401 -> Status.HTTP401_FEIL_AUTENTISERING
                403 -> Status.HTTP403_INGEN_TILGANG
                404 -> {
                    if (content.type.contains(KRAV_EKSISTERER_IKKE)) {
                        Status.HTTP404_FANT_IKKE_SAKSREF
                    } else {
                        Status.HTTP404_ANNEN_IKKE_FUNNET
                    }
                }
                406 -> Status.HTTP406_FEIL_MEDIETYPE
                409 -> {
                    if (content.type.contains(KRAV_IKKE_RESKONTROFORT_RESEND)) {
                        Status.HTTP409_IKKE_RESKONTROFORT_RESEND
                    } else if (content.type.contains(KRAV_ER_AVSKREVET) || content.type.contains(KRAV_ER_ALLEREDE_AVSKREVET)) {
                        Status.HTTP409_KRAV_ER_AVSKREVET
                    } else {
                        Status.HTTP409_ANNEN_KONFLIKT
                    }
                }
                422 -> Status.HTTP422_VALIDERINGSFEIL
                500 -> Status.HTTP500_INTERN_TJENERFEIL
                503 -> Status.HTTP503_UTILGJENGELIG_TJENESTE
                in 300..399 -> Status.HTTP300_REDIRECTION_FEIL
                in 400..499 -> Status.HTTP400_ANNEN_KLIENT_FEIL
                in 500..599 -> Status.HTTP500_ANNEN_SERVER_FEIL

                else -> Status.UKJENT_FEIL
            }
        }


