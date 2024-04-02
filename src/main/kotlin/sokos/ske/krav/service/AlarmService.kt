package sokos.ske.krav.service


import io.ktor.http.*
import sokos.ske.krav.metrics.Metrics.requestError
import sokos.ske.krav.util.RequestResult

object AlarmService {

    fun handleFeil(liste: List<RequestResult>, file: FtpFil) {
        val unsuccessful = liste.filter { !it.response.status.isSuccess() }

        val endringer = unsuccessful.filter { it.krav.kravtype== ENDRING_RENTE || it.krav.kravtype == ENDRING_HOVEDSTOL }
        val stopp = unsuccessful.filter { it.krav.kravtype == STOPP_KRAV }
        val nye = unsuccessful.filter { it.krav.kravtype == NYTT_KRAV }

        if(endringer.isNotEmpty()){
            val message = "Feil i innsending av endring av krav i fil ${file.name}: ${endringer.map {"${it.krav.saksnummerNAV}:  ${it.status}" }}"
            println("HER BURDE VI ALARMERE $message")
            requestError.labels(file.name, message).inc()
        }
        if(stopp.isNotEmpty()) {
            val message = "Feil i innsending av stopp av krav i fil ${file.name}: ${stopp.map {"${it.krav.saksnummerNAV}:  ${it.status}" }}"
            println("HER BURDE VI ALARMERE $message")
            requestError.labels(file.name, message).inc()
        }
        if(nye.isNotEmpty()){
            val message = "Feil i innsending av nytt krav i fil ${file.name}: ${nye.map {"${it.krav.saksnummerNAV}:  ${it.status}" }}"
            println("HER BURDE VI ALARMERE $message")
            requestError.labels(file.name, message).inc()
        }

    }
}