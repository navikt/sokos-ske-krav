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
        onResponse { response ->
            try {
                circuitBreaker.acquirePermission()

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
            } catch (_: CallNotPermittedException) {
                // Breaker is OPEN: we log, but we don't fail the request here since the response already exists.
                // (If you want fail-fast when OPEN, move circuit breaker enforcement to HttpSend instead.)
                logger.warn { "Circuit breaker state is ${circuitBreaker.state} - call not permitted" }
            } catch (e: Exception) {
                // Defensive: we never want the plugin itself to break response handling.
                logger.warn(e) { "Circuit breaker plugin failed to record outcome" }
            }
        }
    }

class CircuitBreakerException(
    message: String,
) : Exception(message)
