package no.nav.sokos.ske.krav.config

import java.time.Duration

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.kotlin.circuitbreaker.decorateSuspendFunction
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

object CircuitBreakerManager {
    private const val CIRCUIT_BREAKER_NAME = "http-client-breaker"
    private val configProperties = PropertiesConfig.CircuitBreakerConfig
    private val config =
        CircuitBreakerConfig
            .custom()
            .slidingWindowSize(configProperties.SLIDING_WINDOW_SIZE)
            .minimumNumberOfCalls(configProperties.MINIMUM_NUMBER_OF_CALLS)
            .failureRateThreshold(configProperties.FAILURE_RATE_THRESHOLD)
            .waitDurationInOpenState(Duration.ofHours(configProperties.waitDurationInOpenState)) // TODO: Juster denne verdien basert på forventet nedetid
            .permittedNumberOfCallsInHalfOpenState(configProperties.PERMITTED_NUMBER_OF_CALLS_IN_HALF_OPEN_STATE)
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
