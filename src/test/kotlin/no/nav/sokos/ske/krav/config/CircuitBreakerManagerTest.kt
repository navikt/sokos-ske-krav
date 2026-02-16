package no.nav.sokos.ske.krav.config

import java.time.Duration
import java.util.concurrent.TimeUnit

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

import no.nav.sokos.ske.krav.config.CircuitBreakerManager.circuitBreaker

class CircuitBreakerManagerTest :
    FunSpec({
        fun waitUntil(
            timeoutMs: Long,
            pollMs: Long,
            condition: () -> Boolean,
        ) {
            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                if (condition()) return
                Thread.sleep(pollMs)
            }
            throw AssertionError("Condition not met within ${timeoutMs}ms")
        }

        beforeEach {
            circuitBreaker.reset()
        }

        test("config should enable automatic transition from OPEN to HALF_OPEN") {
            val config = circuitBreaker.circuitBreakerConfig
            config.isAutomaticTransitionFromOpenToHalfOpenEnabled shouldBe true
        }

        test("auto transition should move OPEN -> HALF_OPEN") {
            val autoConfig =
                CircuitBreakerConfig
                    .custom()
                    .slidingWindowSize(1)
                    .minimumNumberOfCalls(1)
                    .failureRateThreshold(100.0f)
                    .waitDurationInOpenState(Duration.ofMillis(50))
                    .permittedNumberOfCallsInHalfOpenState(1)
                    .automaticTransitionFromOpenToHalfOpenEnabled(true)
                    .build()
            val cb = CircuitBreaker.of("auto-transition", autoConfig)

            cb.transitionToOpenState()
            cb.state shouldBe CircuitBreaker.State.OPEN

            waitUntil(timeoutMs = 500, pollMs = 10) { cb.state == CircuitBreaker.State.HALF_OPEN }
            cb.state shouldBe CircuitBreaker.State.HALF_OPEN
        }

        test("without auto transition, OPEN should stay OPEN") {
            val manualConfig =
                CircuitBreakerConfig
                    .custom()
                    .slidingWindowSize(1)
                    .minimumNumberOfCalls(1)
                    .failureRateThreshold(100.0f)
                    .waitDurationInOpenState(Duration.ofMillis(50))
                    .permittedNumberOfCallsInHalfOpenState(1)
                    .automaticTransitionFromOpenToHalfOpenEnabled(false)
                    .build()
            val cb = CircuitBreaker.of("manual-transition", manualConfig)

            cb.transitionToOpenState()
            cb.state shouldBe CircuitBreaker.State.OPEN

            Thread.sleep(150)
            cb.state shouldBe CircuitBreaker.State.OPEN
        }

        test("reset should close the circuit breaker") {

            circuitBreaker.transitionToOpenState()
            circuitBreaker.state shouldBe CircuitBreaker.State.OPEN

            circuitBreaker.reset()
            circuitBreaker.state shouldBe CircuitBreaker.State.CLOSED
        }

        test("state should transition from OPEN to HALF_OPEN") {
            circuitBreaker.transitionToOpenState()
            circuitBreaker.state shouldBe CircuitBreaker.State.OPEN

            circuitBreaker.transitionToHalfOpenState()
            circuitBreaker.state shouldBe CircuitBreaker.State.HALF_OPEN
        }

        test("state should transition from HALF_OPEN to CLOSED") {
            circuitBreaker.transitionToOpenState()
            circuitBreaker.state shouldBe CircuitBreaker.State.OPEN

            circuitBreaker.transitionToHalfOpenState()
            circuitBreaker.state shouldBe CircuitBreaker.State.HALF_OPEN

            circuitBreaker.transitionToClosedState()
            circuitBreaker.state shouldBe CircuitBreaker.State.CLOSED
        }

        test("successful call in HALF_OPEN should close circuit") {
            circuitBreaker.transitionToOpenState()
            circuitBreaker.state shouldBe CircuitBreaker.State.OPEN

            circuitBreaker.transitionToHalfOpenState()
            circuitBreaker.state shouldBe CircuitBreaker.State.HALF_OPEN

            shouldNotThrowAny {
                circuitBreaker.onSuccess(0, TimeUnit.NANOSECONDS)
            }

            circuitBreaker.transitionToClosedState()
            circuitBreaker.state shouldBe CircuitBreaker.State.CLOSED
        }
    })
