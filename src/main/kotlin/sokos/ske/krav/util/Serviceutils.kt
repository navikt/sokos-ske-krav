package sokos.ske.krav.util

import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import sokos.ske.krav.config.secureLogger
import sokos.ske.krav.database.models.KravTable
import sokos.ske.krav.domain.ske.requests.KravidentifikatorType
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

suspend inline fun <reified T> HttpResponse.parseTo(): T? =
    runCatching { body<T>() }
        .onFailure {
            secureLogger.error { "Error decoding JSON to ${T::class.simpleName}: ${it.message}" }
        }.getOrNull()
