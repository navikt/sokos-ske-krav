package no.nav.sokos.ske.krav.config

import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.request
import io.ktor.http.HttpStatusCode

private val logger = mu.KotlinLogging.logger {}

private fun HttpResponse.isFailure() = status == HttpStatusCode.Unauthorized || status == HttpStatusCode.Forbidden || status.value in 500..599

val CircuitBreakerPlugin =
    createClientPlugin("CircuitBreakerPlugin") {
        val circuitBreaker = CircuitBreakerManager.circuitBreaker
        onResponse { response ->
            try {
                circuitBreaker.executeSupplier {
                    if (response.isFailure()) {
                        logger.error {
                            "Circuit breaker recording failure: ${response.status.value} for ${response.request.url}"
                        }

                        throw CircuitBreakerException(
                            "HTTP ${response.status.value}: ${response.status.description}",
                        )
                    }
                    response
                }
            } catch (e: CallNotPermittedException) {
                logger.warn { "Circuit breaker state is ${circuitBreaker.state} - call not permitted" }
                throw CircuitBreakerStateException("Circuit breaker state is ${circuitBreaker.state} - call not permitted")
            }
        }
    }

class CircuitBreakerException(
    message: String,
) : Exception(message)

class CircuitBreakerStateException(
    message: String,
) : Exception(message)
