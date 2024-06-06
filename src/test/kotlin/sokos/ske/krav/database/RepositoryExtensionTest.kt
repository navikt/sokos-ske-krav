package sokos.ske.krav.database

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import sokos.ske.krav.database.RepositoryExtensions.getColumn
import sokos.ske.krav.database.RepositoryExtensions.toFeilmelding
import sokos.ske.krav.database.RepositoryExtensions.useAndHandleErrors
import sokos.ske.krav.util.startContainer
import java.sql.SQLException
import java.util.UUID

internal class RepositoryExtensionTest: FunSpec({

    val emptyDB = startContainer(UUID.randomUUID().toString(), emptyList())
    test("getColumn skal kaste exception hvis den ikke kan parse datatypen") {
        shouldThrow<SQLException> {
            emptyDB.connection.use {
                val rs = it.prepareStatement("""select * from feilmelding""").executeQuery()
                rs.getColumn("any")
            }
        }
    }

    test("resultset getcolumn skal kaste exception hvis den ikke finner kolonne med det gitte navnet") {
        shouldThrow<SQLException> {
            emptyDB.connection.use {
                val rs = it.prepareStatement("""select * from feilmelding""").executeQuery()
                rs.getColumn("foo")
            }
        }
    }
    test("resultset getcolumn skal kaste exception hvis påkrevd column er null") {
        shouldThrow<SQLException> {
            emptyDB.connection.use {
                it.prepareStatement(
                    """
                    insert into feilmelding ( kravID, corr_id, saksnummer_nav, kravidentifikator_ske, error, melding, nav_request, ske_response, tidspunkt_opprettet)
                    values  (1, 'CORR769', '3330-navsaksnummer', '3333-skeUUID', 422, 'feilmelding 422 3333', '{nav request 3}', '{ske response 3}', null);
                """.trimIndent()
                ).execute()
                val rs = it.prepareStatement("""select * from feilmelding""").executeQuery()
                rs.toFeilmelding()
            }
        }
    }
    test("useAndHandleErrors skal kaste exception oppover") {
        shouldThrow<SQLException> {
            emptyDB.connection.useAndHandleErrors {
                it.prepareStatement("""insert into foo values(1,2)""").execute()
            }
        }
    }
})