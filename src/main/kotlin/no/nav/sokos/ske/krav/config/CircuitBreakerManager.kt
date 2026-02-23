package no.nav.sokos.ske.krav.config

import java.time.Duration

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.kotlin.circuitbreaker.decorateSuspendFunction
import mu.KotlinLogging

import no.nav.sokos.ske.krav.config.CircuitBreakerConfig.Companion.FAILURE_RATE_THRESHOLD
import no.nav.sokos.ske.krav.config.CircuitBreakerConfig.Companion.MINIMUM_NUMBER_OF_CALLS
import no.nav.sokos.ske.krav.config.CircuitBreakerConfig.Companion.PERMITTED_NUMBER_OF_CALLS_IN_HALF_OPEN_STATE
import no.nav.sokos.ske.krav.config.CircuitBreakerConfig.Companion.SLIDING_WINDOW_SIZE
import no.nav.sokos.ske.krav.config.PropertiesConfigNew.circuitBreakerConfig

private val logger = KotlinLogging.logger {}

object CircuitBreakerManager {
    private const val CIRCUIT_BREAKER_NAME = "http-client-breaker"
    private val config =
        CircuitBreakerConfig
            .custom()
            .slidingWindowSize(SLIDING_WINDOW_SIZE)
            .minimumNumberOfCalls(MINIMUM_NUMBER_OF_CALLS)
            .failureRateThreshold(FAILURE_RATE_THRESHOLD)
            .waitDurationInOpenState(Duration.ofHours(circuitBreakerConfig.waitDurationInOpenState)) // TODO: Juster denne verdien basert på forventet nedetid
            .permittedNumberOfCallsInHalfOpenState(PERMITTED_NUMBER_OF_CALLS_IN_HALF_OPEN_STATE)
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

    suspend fun <T> guardCall(block: suspend () -> T): T = circuitBreaker.decorateSuspendFunction(block)()
}
