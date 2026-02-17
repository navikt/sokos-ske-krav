package no.nav.sokos.ske.krav.config

import java.util.concurrent.TimeUnit

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
            if (response.isFailure()) {
                logger.error {
                    "Circuit breaker recording failure: ${response.status.value} for ${response.request.url}. " +
                        "Circuit breaker state is ${circuitBreaker.state}"
                }
                circuitBreaker.onError(
                    0L,
                    TimeUnit.MILLISECONDS,
                    CircuitBreakerException("HTTP ${response.status.value}: ${response.status.description}"),
                )
            } else {
                circuitBreaker.onSuccess(0L, TimeUnit.MILLISECONDS)
            }
        }
    }

class CircuitBreakerException(
    message: String,
) : Exception(message)
