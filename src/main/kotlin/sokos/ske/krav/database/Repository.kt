package sokos.ske.krav.database

import mu.KotlinLogging
import sokos.ske.krav.database.RepositoryExtensions.toOpprettInnkrevingsOppdragResponse
import sokos.ske.krav.skemodels.responses.OpprettInnkrevingsOppdragResponse
import java.sql.Connection

object Repository {
    private val log = KotlinLogging.logger {}
    fun Connection.hentKravData(): List<OpprettInnkrevingsOppdragResponse> {
        return try {
            prepareStatement("""SELECT * FROM KRAV""").executeQuery().toOpprettInnkrevingsOppdragResponse()
        } catch(e: Exception){
            log.error("exception i henting av data: ${e.message}")
            listOf()
        }

    }
}