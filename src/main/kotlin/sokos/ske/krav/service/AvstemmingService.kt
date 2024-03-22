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

    fun hentAvstemmingsRapport(): String {
        val dato = LocalDate.now()

        val header = hentheader();
        val body = hentBody(dato);
        val footer = hentFooter();
        return "$header $body $footer"

    }

    fun hentAvstemminsRapportSomFil(): String {
        val header = "Krav-Id,Vedtaks-Id,Fagsystem-Id,Registrert,Kravkode,Hjemmelskode,Status,StatusDato\n"
        val linjer = databaseService.hentKravSomSkalAvstemmes().map {
            "${it.kravId},${it.saksnummerNAV},${it.tidspunktOpprettet},${it.kravkode},${it.kodeHjemmel},${it.status},${it.tidspunktSisteStatus}"
        }.joinToString("\n")
        return header + linjer
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
            """<meta charset=\"utf-8\" />
            <title>Åpne statuser</title>
            </head>
            <body><H1>Åpne statuser</H1>
            <table width="80%" border="2" cellpadding="10"><tr>
            <th>Krav-Id</th>
            <th>Vedtaks-Id</th>
            <th>Fagsystem-Id</th>
            <th>Registrert</th>
            <th>Kravkode</th>
            <th>Hjemmelskode</th>
            <th>Status</th>
            <th>StatusDato</th></tr> 
            """

    private fun hentFooter() = """
       </table>
       <form action ="avstemming/fil" method="get">
       <p><input type="submit" value="Last ned .csv fil"></>
       </body>
       </html>
    """.trimIndent()


}
