package no.nav.sokos.ske.krav.config

import java.time.Duration

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.kotlin.circuitbreaker.decorateSuspendFunction
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

object CircuitBreakerManager {
    private const val CIRCUIT_BREAKER_NAME = "http-client-breaker"

    private val config =
        CircuitBreakerConfig
            .custom()
            .slidingWindowSize(1)
            .minimumNumberOfCalls(1)
            .failureRateThreshold(100.0f)
            .waitDurationInOpenState(Duration.ofHours(3L)) // TODO: Juster denne verdien basert på forventet nedetid
            .permittedNumberOfCallsInHalfOpenState(1)
            .automaticTransitionFromOpenToHalfOpenEnabled(true)
            .build()

    val circuitBreaker: CircuitBreaker =
        CircuitBreaker.of(CIRCUIT_BREAKER_NAME, config).apply {
            eventPublisher
                .onStateTransition { event ->
                    logger.info {
                        "Circuit breaker state changed: ${event.stateTransition.fromState} -> ${event.stateTransition.toState}"
                    }
                }
        }

    // Brukes bare midlertidig i test
    val state: CircuitBreaker.State
        get() = circuitBreaker.state

    // Brukes bare midlertidig i test
    fun reset() {
        circuitBreaker.reset()
        logger.info { "Circuit breaker reset to CLOSED" }
    }

    suspend fun <T> guardCall(block: suspend () -> T): T = circuitBreaker.decorateSuspendFunction(block)()
}
