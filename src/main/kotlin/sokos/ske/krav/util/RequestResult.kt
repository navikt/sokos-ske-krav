package sokos.ske.krav.util

import io.ktor.client.call.*
import io.ktor.client.statement.*
import io.ktor.http.*
import sokos.ske.krav.database.models.KravTable
import sokos.ske.krav.database.models.Status
import sokos.ske.krav.domain.ske.responses.FeilResponse

const val KRAV_IKKE_RESKONTROFORT_RESEND = "tag:skatteetaten.no,2024:innkreving:innkrevingsoppdrag:innkrevingsoppdrag-er-ikke-reskontrofoert"
const val KRAV_ER_AVSKREVET = "tag:skatteetaten.no,2024:innkreving:innkrevingsoppdrag:innkrevingsoppdrag-er-avskrevet"
const val KRAV_ER_ALLEREDE_AVSKREVET = "tag:skatteetaten.no,2024:innkreving:innkrevingsoppdrag:innkrevingsoppdrag-er-allerede-avskrevet"
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
    when (response.status.value) {
        404 -> return Status.FANT_IKKE_SAKSREF
        409 -> if (content.type == KRAV_IKKE_RESKONTROFORT_RESEND)
            return Status.IKKE_RESKONTROFORT_RESEND
        else if (content.type == KRAV_ER_AVSKREVET || content.type == KRAV_ER_ALLEREDE_AVSKREVET)
            return Status.ER_AVSKREVET

        422 -> return Status.VALIDERINGSFEIL
        else -> return Status.UKJENT_FEIL
    }
    return Status.UKJENT_STATUS
}
