package no.nav.sokos.ske.krav.config

import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.request
import io.ktor.http.HttpStatusCode

private val logger = mu.KotlinLogging.logger {}

fun HttpResponse.isFailure() =
    status == HttpStatusCode.Unauthorized ||
        status == HttpStatusCode.Forbidden ||
        status.value in 500..599

val CircuitBreakerPlugin =
    createClientPlugin("CircuitBreakerPlugin") {
        val circuitBreaker = CircuitBreakerManager.circuitBreaker
        onResponse { response ->
            if (response.isFailure()) {
                logger.error {
                    "Circuit breaker treating response as failure: ${response.status.value} for ${response.request.url} "
                }
                throw CircuitBreakerException("HTTP ${response.status.value}: ${response.status.description} from ${response.request.url}")
            }
        }
    }

class CircuitBreakerException(
    message: String,
) : Exception(message)
