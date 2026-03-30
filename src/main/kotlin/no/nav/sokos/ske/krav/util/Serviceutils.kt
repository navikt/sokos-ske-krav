package no.nav.sokos.ske.krav.util

import no.nav.sokos.ske.krav.config.TEAM_LOGS_MARKER
import no.nav.sokos.ske.krav.config.jsonConfig
import no.nav.sokos.ske.krav.domain.Krav
import no.nav.sokos.ske.krav.dto.ske.requests.KravidentifikatorType
import no.nav.sokos.ske.krav.service.NYTT_KRAV

@PublishedApi
internal val logger = mu.KotlinLogging.logger {}

// TODO: Refaktorer? Uklart navn
fun createKravidentifikatorPair(it: Krav): Pair<String, KravidentifikatorType> {
    var kravIdentifikator = it.kravidentifikatorSKE
    var kravIdentifikatorType = KravidentifikatorType.SKATTEETATENSKRAVIDENTIFIKATOR

    if (kravIdentifikator.isEmpty() && it.kravtype != NYTT_KRAV) {
        kravIdentifikator = it.referansenummerGammelSak
        kravIdentifikatorType = KravidentifikatorType.OPPDRAGSGIVERSKRAVIDENTIFIKATOR
    }
    return Pair(kravIdentifikator, kravIdentifikatorType)
}

inline fun <reified T> String.decodeTo(): T? =
    runCatching {
        jsonConfig.decodeFromString<T>(this)
    }.onFailure { e ->
        logger.error(marker = TEAM_LOGS_MARKER) {
            "Error decoding JSON to ${T::class.simpleName} (input length=${this.length}): ${e.message}"
        }
    }.getOrNull()

inline fun <reified T> T.encodeToString(): String =
    runCatching {
        jsonConfig.encodeToString(this)
    }.onFailure { e ->
        logger.error(marker = TEAM_LOGS_MARKER) { "Error encoding ${T::class.simpleName} to JSON: ${e.message}" }
    }.getOrDefault("")
