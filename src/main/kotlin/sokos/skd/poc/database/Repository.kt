package sokos.skd.poc.database

import mu.KotlinLogging
import sokos.skd.poc.database.RepositoryExtensions.toOpprettInnkrevingsOppdragResponse
import sokos.skd.poc.skdmodels.NyttOppdrag.OpprettInnkrevingsOppdragResponse
import java.sql.Connection

object Repository {
    private val log = KotlinLogging.logger {}
    fun Connection.hentKravData(): List<OpprettInnkrevingsOppdragResponse> {
        return try {
            prepareStatement("""SELECT * FROM KRAV_DATA""").executeQuery().toOpprettInnkrevingsOppdragResponse()
        } catch(e: Exception){
            log.error("exception i henting av data: ${e.message}")
            listOf()
        }

    }
}