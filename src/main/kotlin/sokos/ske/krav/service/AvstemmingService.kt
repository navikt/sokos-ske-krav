package sokos.ske.krav.service

import mu.KotlinLogging
import java.time.LocalDate

class AvstemmingService(
  private val databaseService: DatabaseService,
) {
  private val logger = KotlinLogging.logger {}

  private suspend fun avstemmKrav() {
	  val krav = databaseService.hentKravSomSkalAvstemmes()
	  val totaltAntall = krav.size;
      println("Antall som skal avstemmes $totaltAntall")
  }

    fun hentAvstemmingsRapport():String {
        val dato = LocalDate.now()

        val header = hentheader();
        val body = hentBody(dato);
        val footer = hentFooter();
        return "$header $body $footer"

    }

    private fun hentBody(dato: LocalDate) = databaseService.hentKravSomSkalAvstemmes().map {
            "<tr><td>${it.kravId}</td>" +
                    "<td>${it.saksnummerNAV}</td>" +
                    "<td>${it.fagsystemId}</td>" +
                    "<td>${it.tidspunktOpprettet}</td>" +
                    "<td>${it.kravkode}</td>" +
                    "<td>${it.kodeHjemmel}</td>" +
                    "<td>${it.status}</td>" +
                    "<td>${it.tidspunktSisteStatus}</td></tr>"
        }.joinToString()

    private fun hentheader() = "<!doctype html><head>\n" +
            "<meta charset=\"utf-8\" />\n" +
            "<title>Avstemmingsrapport</title>\n" +
            "</head>\n" +
            "<body><br> <H1>Avstemmingsrapport <br>" +
            "<table><th>" +
            "<td>Krav-Id</td>" +
            "<td>Vedtaks-Id</td>" +
            "<td>Fagsystem-Id</td>" +
            "<td>Registrert</td>" +
            "<td>Kravkode</td>" +
            "<td>Hjemmelskode</td>" +
            "<td>Status</td>" +
            "<td>StatusDato</td></th>"

    private fun hentFooter() = "</table></body>\n" +
            "</html>"


}
