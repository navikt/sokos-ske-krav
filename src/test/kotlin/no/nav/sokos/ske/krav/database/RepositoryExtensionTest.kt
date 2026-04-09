package no.nav.sokos.ske.krav.database

import java.math.BigDecimal
import java.sql.SQLException

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

import no.nav.sokos.ske.krav.listener.DBListener
import no.nav.sokos.ske.krav.repository.RepositoryExtensions.executeSelect
import no.nav.sokos.ske.krav.repository.RepositoryExtensions.executeUpdate
import no.nav.sokos.ske.krav.repository.RepositoryExtensions.getColumn
import no.nav.sokos.ske.krav.repository.RepositoryExtensions.useAndHandleErrors
import no.nav.sokos.ske.krav.repository.toFeilmelding

internal class RepositoryExtensionTest :
    FunSpec({
        extensions(DBListener)

        test("getColumn skal kaste exception hvis den ikke kan parse datatypen") {
            shouldThrow<SQLException> {
                DBListener.dataSource.connection.use {
                    val rs = it.prepareStatement("""select * from feilmelding""").executeQuery()
                    rs.getColumn("any")
                }
            }
        }

        test("resultset getcolumn skal kaste exception hvis den ikke finner kolonne med det gitte navnet") {
            shouldThrow<SQLException> {
                DBListener.dataSource.connection.use {
                    val rs = it.prepareStatement("""select * from feilmelding""").executeQuery()
                    rs.getColumn("foo")
                }
            }
        }
        test("resultset getcolumn skal kaste exception hvis påkrevd column er null") {
            shouldThrow<SQLException> {
                DBListener.dataSource.connection.use {
                    it
                        .prepareStatement(
                            """
                            insert into feilmelding ( kravID, corr_id, saksnummer_nav, kravidentifikator_ske, error, melding, nav_request, ske_response, tidspunkt_opprettet)
                            values  (1, 'CORR769', '3330-navsaksnummer', '3333-skeUUID', 422, 'feilmelding 422 3333', '{nav request 3}', '{ske response 3}', null);
                            """.trimIndent(),
                        ).execute()
                    val rs = it.prepareStatement("""select * from feilmelding""").executeQuery()
                    rs.toFeilmelding()
                }
            }
        }
        test("useAndHandleErrors skal kaste exception oppover") {
            shouldThrow<SQLException> {
                DBListener.dataSource.connection.use { conn ->
                    conn.useAndHandleErrors {
                        it.prepareStatement("""insert into foo values(1,2)""").execute()
                    }
                }
            }
        }

        test("withParameters skal binde BigDecimal med full presisjon via setBigDecimal") {
            val highPrecisionValue = BigDecimal("123456789.1234567890")

            DBListener.dataSource.connection.use { conn ->
                conn.executeUpdate("CREATE TEMPORARY TABLE IF NOT EXISTS bigdecimal_precision_test (val NUMERIC(28, 10))")
                conn.executeUpdate("INSERT INTO bigdecimal_precision_test (val) VALUES (?)", highPrecisionValue)
                val rs = conn.executeSelect("SELECT val FROM bigdecimal_precision_test")
                rs.next()
                rs.getColumn<BigDecimal>("val").compareTo(highPrecisionValue) shouldBe 0
            }
        }
    })
