package no.nav.sokos.ske.krav.util

import java.sql.SQLException

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotliquery.queryOf
import org.slf4j.LoggerFactory

import no.nav.sokos.ske.krav.config.TEAM_LOGS_MARKER
import no.nav.sokos.ske.krav.listener.DBListener

class DBUtilsTest :
    FunSpec({
        extensions(DBListener)

        val dbUtilLogger = LoggerFactory.getLogger("no.nav.sokos.ske.krav.util.DBUtils") as Logger
        val logAppender = ListAppender<ILoggingEvent>()

        beforeTest {
            logAppender.start()
            dbUtilLogger.addAppender(logAppender)
        }

        afterTest {
            dbUtilLogger.detachAppender(logAppender)
            logAppender.stop()
        }

        test("transaction skal kaste exception oppover og logge error i begge vanlig log og TEAM_LOGS") {
            shouldThrow<SQLException> {
                DBListener.dataSource.transaction { session ->
                    session.update(queryOf("insert into foo values(1,2)"))
                }
            }

            val sqlExceptionMessage = """ERROR: relation "foo" does not exist"""

            val messages = logAppender.list
            messages.shouldHaveSize(2)

            messages.first().apply {
                markerList.shouldBeNull()
                formattedMessage shouldNotContain sqlExceptionMessage
            }

            messages.last().apply {
                markerList.shouldContain(TEAM_LOGS_MARKER)
                formattedMessage shouldContain sqlExceptionMessage
            }
        }
    })
