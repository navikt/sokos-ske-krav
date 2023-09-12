package sokos.ske.krav.database

import sokos.ske.krav.skemodels.responses.OpprettInnkrevingsOppdragResponse
import java.sql.ResultSet

object RepositoryExtensions {

    fun ResultSet.toOpprettInnkrevingsOppdragResponse() = toList {
        OpprettInnkrevingsOppdragResponse(
            kravidentifikator = getString("KRAVIDENTIFIKATOR")
        )
    }

    private fun <T> ResultSet.toList(mapper: ResultSet.() -> T) = mutableListOf<T>().apply {
        while (next()) {
            add(mapper())
        }
    }
}