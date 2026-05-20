package no.nav.sokos.ske.krav.util

import java.sql.SQLException

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotliquery.queryOf
import mu.KLogger
import mu.KotlinLogging
import mu.Marker

import no.nav.sokos.ske.krav.config.TEAM_LOGS_MARKER
import no.nav.sokos.ske.krav.listener.DBListener

class DBUtilsTest :
    FunSpec({
        extensions(DBListener)

        val loggerMock = mockk<KLogger>(relaxed = true)

        beforeSpec {
            mockkObject(KotlinLogging)
            every { KotlinLogging.logger(any<() -> Unit>()) } returns loggerMock
        }

        test("transaction skal kaste exception oppover og logge error i begge vanlig log og TEAM_LOGS") {
            justRun { loggerMock.error(any<String>()) }
            justRun { loggerMock.error(any<Marker>(), any<String>()) }

            shouldThrow<SQLException> {
                DBListener.dataSource.transaction { session ->
                    session.update(queryOf("insert into foo values(1,2)"))
                }
            }
            val errorMessages = mutableListOf<String>()
            verify(exactly = 1) {
                loggerMock.error(capture(errorMessages))
                loggerMock.error(eq(TEAM_LOGS_MARKER), capture(errorMessages))
            }

            val sqlExceptionMessage = """ERROR: relation "foo" does not exist"""
            errorMessages.first() shouldNotContain sqlExceptionMessage
            errorMessages.last() shouldContain sqlExceptionMessage
        }

        afterSpec {
            unmockkObject(KotlinLogging)
        }
    })
