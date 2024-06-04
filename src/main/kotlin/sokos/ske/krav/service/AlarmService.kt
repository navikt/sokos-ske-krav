package sokos.ske.krav.service


import io.ktor.http.*
import kotlinx.datetime.Clock
import sokos.ske.krav.metrics.Metrics.requestError
import sokos.ske.krav.util.RequestResult

object AlarmService {

    fun handleFeil(liste: List<RequestResult>, file: FtpFil) {
        val unsuccessful = liste.filter { !it.response.status.isSuccess() }

        val endringer = unsuccessful.filter { it.kravTable.kravtype== ENDRING_RENTE || it.kravTable.kravtype == ENDRING_HOVEDSTOL }
        val stopp = unsuccessful.filter { it.kravTable.kravtype == STOPP_KRAV }
        val nye = unsuccessful.filter { it.kravTable.kravtype == NYTT_KRAV }

        if(endringer.isNotEmpty()){
            val message = "${Clock.System.now()}\nFeil i innsending av endring av krav i fil ${file.name}: ${endringer.map {"\n${it.kravTable.saksnummerNAV}:  ${it.status}" }}"
            requestError.inc()
        }
        if(stopp.isNotEmpty()) {
            val message = "${Clock.System.now()}\nFeil i innsending av stopp av krav i fil ${file.name}: ${stopp.map {"\n${it.kravTable.saksnummerNAV}:  ${it.status}" }}"
            requestError.inc()
        }
        if(nye.isNotEmpty()){
            val message = "${Clock.System.now()}\nFeil i innsending av nytt krav i fil ${file.name}: ${nye.map {"\n${it.kravTable.saksnummerNAV}:  ${it.status}" }}"
            requestError.inc()
        }

    }
}