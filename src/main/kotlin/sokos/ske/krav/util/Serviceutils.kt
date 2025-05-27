package sokos.ske.krav.util

import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import kotlinx.serialization.json.Json
import sokos.ske.krav.database.models.KravTable
import sokos.ske.krav.domain.ske.requests.KravidentifikatorType
import sokos.ske.krav.domain.ske.responses.FeilResponse
import sokos.ske.krav.service.NYTT_KRAV

fun createKravidentifikatorPair(it: KravTable): Pair<String, KravidentifikatorType> {
    var kravIdentifikator = it.kravidentifikatorSKE
    var kravIdentifikatorType = KravidentifikatorType.SKATTEETATENSKRAVIDENTIFIKATOR

    if (kravIdentifikator.isEmpty() && it.kravtype != NYTT_KRAV) {
        kravIdentifikator = it.referansenummerGammelSak
        kravIdentifikatorType = KravidentifikatorType.OPPDRAGSGIVERSKRAVIDENTIFIKATOR
    }
    return Pair(kravIdentifikator, kravIdentifikatorType)
}

suspend inline fun <reified T> HttpResponse.parseTo(): T? {
    val logger = mu.KotlinLogging.logger {}
    return try {
        body<T>()
    } catch (e: Exception) {
        try {
            val feilResponse = body<FeilResponse>()
            logger.error { "Valideringsfeil mottatt: ${feilResponse.title}" }
            null
        } catch (e2: Exception) {
            logger.error(e2) { "Error decoding JSON to ${T::class.simpleName} and failed to parse error response: ${e2.message}" }
            null
        }
    }
}

inline fun <reified T> T.encodeToString(): String {
    val logger = mu.KotlinLogging.logger {}
    return runCatching {
        Json.encodeToString(this)
    }.onFailure { exception ->
        logger.error(exception) { "Error encoding JSON to ${T::class.simpleName}: ${exception.message}" }
    }.getOrDefault("Error encoding JSON to ${T::class.simpleName}")
}