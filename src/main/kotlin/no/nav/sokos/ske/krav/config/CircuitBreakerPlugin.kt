package no.nav.sokos.ske.krav.config

import io.github.resilience4j.circuitbreaker.CallNotPermittedException
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
            try {
                if (response.isFailure()) {
                    logger.error {
                        "Circuit breaker treating response as failure: ${response.status.value} for ${response.request.url} "
                    }
                    throw CircuitBreakerException("HTTP ${response.status.value}: ${response.status.description} from ${response.request.url}")
                }
            } catch (_: CallNotPermittedException) {
                logger.warn { "Circuit breaker state is ${circuitBreaker.state} - call not permitted" }
            }
        }
    }

class CircuitBreakerException(
    message: String,
) : Exception(message)
