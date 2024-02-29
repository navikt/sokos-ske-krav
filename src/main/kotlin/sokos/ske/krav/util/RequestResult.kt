package sokos.ske.krav.util

import io.ktor.client.call.*
import io.ktor.client.statement.*
import io.ktor.http.*
import sokos.ske.krav.database.models.KravTable
import sokos.ske.krav.database.models.Status
import sokos.ske.krav.domain.ske.responses.FeilResponse
import sokos.ske.krav.util.ErrorTypeSke.*

data class RequestResult(
    val response: HttpResponse,
    val krav: KravTable,
    val request: String,
    val kravIdentifikator: String,
    val corrId: String,
    val status: Status
)
suspend fun defineStatus(response: HttpResponse):Status {
    if (response.status.isSuccess()) return Status.KRAV_SENDT
    val content = response.body<FeilResponse>()

    return when (response.status.value) {
            404 -> Status.FANT_IKKE_SAKSREF
            409 -> {
                if (content.type.contains(KRAV_IKKE_RESKONTROFORT_RESEND.value)) {
                    return Status.IKKE_RESKONTROFORT_RESEND
                }
                else if (content.type.contains(KRAV_ER_AVSKREVET.value) ||
                    content.type.contains(KRAV_ER_ALLEREDE_AVSKREVET.value)
                    ) {
                    Status.ER_AVSKREVET
                }else {
                    Status.ANNEN_KONFLIKT
                }
            }
            422 -> Status.VALIDERINGSFEIL
            else ->  Status.UKJENT_FEIL
        }
}
