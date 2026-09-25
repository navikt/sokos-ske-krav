package no.nav.sokos.ske.krav.scheduling

import java.time.LocalDateTime
import java.time.Month

import io.kotest.common.ExperimentalKotest
import io.kotest.common.KotestInternal
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

@OptIn(ExperimentalKotest::class, KotestInternal::class)
class SchedulerTest :
    FunSpec({
        coroutineTestScope = true

        val scheduler by lazy {
            Scheduler(scope)
        }

        val now = LocalDateTime.of(2026, Month.SEPTEMBER, 25, 11, 25, 5, 6)

        test("Scheduling at an hour before the current hour increments the day by one") {
            scheduler.defineNext(now, hour = 10) shouldBe LocalDateTime.of(2026, Month.SEPTEMBER, 26, 10, 25, 0, 0)
        }

        test("Scheduling at an hour after the current hour schedules for the same day") {
            scheduler.defineNext(now, hour = 12) shouldBe LocalDateTime.of(2026, Month.SEPTEMBER, 25, 12, 25, 0, 0)
        }

        test("Scheduling at a minute before the current minute increments the hour by one") {
            scheduler.defineNext(now, minute = 10) shouldBe LocalDateTime.of(2026, Month.SEPTEMBER, 25, 12, 10, 0, 0)
        }

        test("Scheduling at a minute after the current minute schedules for the same hour") {
            scheduler.defineNext(now, minute = 30) shouldBe LocalDateTime.of(2026, Month.SEPTEMBER, 25, 11, 30, 0, 0)
        }

        test("Scheduling at a second before the current second increments the minute by one") {
            scheduler.defineNext(now, second = 1) shouldBe LocalDateTime.of(2026, Month.SEPTEMBER, 25, 11, 26, 1, 0)
        }

        test("Scheduling at a second after the current second schedules for the same minute") {
            scheduler.defineNext(now, second = 20) shouldBe LocalDateTime.of(2026, Month.SEPTEMBER, 25, 11, 25, 20, 0)
        }
    })
