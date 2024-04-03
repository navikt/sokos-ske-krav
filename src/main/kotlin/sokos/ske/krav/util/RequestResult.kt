package sokos.ske.krav.util

import io.ktor.client.call.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import sokos.ske.krav.database.models.KravTable
import sokos.ske.krav.database.models.Status
import sokos.ske.krav.domain.ske.responses.FeilResponse

const val KRAV_IKKE_RESKONTROFORT_RESEND = "innkrevingsoppdrag-er-ikke-reskontrofoert"
const val KRAV_ER_AVSKREVET = "innkrevingsoppdrag-er-avskrevet"
const val KRAV_ER_ALLEREDE_AVSKREVET = "innkrevingsoppdrag-er-allerede-avskrevet"
const val KRAV_EKSISTERER_IKKE = "innkrevingsoppdrag-eksisterer-ikke"


data class RequestResult(
    val response: HttpResponse,
    val krav: KravTable,
    val request: String,
    val kravidentifikator: String,
    val status: Status = defineStatus(response)
){
    private companion object {
       fun defineStatus(response: HttpResponse): Status {
            if (response.status.isSuccess()) return Status.KRAV_SENDT
            val content = runBlocking {   response.body<FeilResponse>()}

            return when (response.status.value) {
                400 -> Status.UGYLDIG_FORESPORSEL_400
                401 -> Status.FEIL_AUTENTISERING_401
                403 -> Status.INGEN_TILGANG_403
                404 -> {
                    if (content.type.contains(KRAV_EKSISTERER_IKKE)) Status.FANT_IKKE_SAKSREF_404
                    else Status.ANNEN_IKKE_FUNNET_404
                }

                406 -> Status.FEIL_MEDIETYPE_406
                409 -> {
                    if (content.type.contains(KRAV_IKKE_RESKONTROFORT_RESEND)) Status.IKKE_RESKONTROFORT_RESEND
                    else if (content.type.contains(KRAV_ER_AVSKREVET) || content.type.contains(KRAV_ER_ALLEREDE_AVSKREVET))
                        Status.KRAV_ER_AVSKREVET_409
                    else Status.ANNEN_KONFLIKT_409
                }

                422 -> Status.VALIDERINGSFEIL_422
                500 -> Status.INTERN_TJENERFEIL_500
                503 -> Status.UTILGJENGELIG_TJENESTE_503

                in 300 ..399 -> Status.REDIRECTION_FEIL_300
                in 400 ..499 -> Status.ANNEN_KLIENT_FEIL_400
                in 500 ..599 -> Status.ANNEN_SERVER_FEIL_500

                else -> Status.UKJENT_FEIL
            }
        }
    }

}

