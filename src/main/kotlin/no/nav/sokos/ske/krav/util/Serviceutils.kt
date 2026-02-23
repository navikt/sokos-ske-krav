package no.nav.sokos.ske.krav.util

import kotlinx.serialization.json.Json

import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse

import no.nav.sokos.ske.krav.config.TEAM_LOGS_MARKER
import no.nav.sokos.ske.krav.config.jsonConfig
import no.nav.sokos.ske.krav.domain.Krav
import no.nav.sokos.ske.krav.dto.ske.requests.KravidentifikatorType
import no.nav.sokos.ske.krav.dto.ske.responses.FeilResponse
import no.nav.sokos.ske.krav.service.NYTT_KRAV

val logger = mu.KotlinLogging.logger {}

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
        val response = jsonConfig.decodeFromString<T>(this)
        if (T::class == FeilResponse::class) {
            logger.warn { "Feil mottatt: ${(response as FeilResponse).title}" }
        }
        response
    }.onFailure { e ->
        runCatching {
            val response = jsonConfig.decodeFromString<FeilResponse>(this)
            logger.warn { "Feil mottatt: ${response.title}" }
            response
        }.onFailure {
            logger.error(marker = TEAM_LOGS_MARKER) { "Error decoding JSON to ${T::class.simpleName}: ${e.message}" }
        }.getOrNull()
    }.getOrNull()

suspend inline fun <reified T> HttpResponse.parseTo(): T? =
    runCatching {
        val response = body<T>()
        if (T::class == FeilResponse::class) {
            logger.warn { "Valideringsfeil mottatt: ${(response as FeilResponse).title}" }
        }
        response
    }.onFailure { e ->
        runCatching { body<FeilResponse>() }.getOrElse {
            logger.error { "Error decoding JSON to ${T::class.simpleName}" }
        }
    }.getOrNull()

inline fun <reified T> T.encodeToString(): String =
    runCatching {
        Json.encodeToString(this)
    }.onFailure { e ->
        logger.error { "Error encoding JSON to ${T::class.simpleName}" }
    }.getOrDefault("")
