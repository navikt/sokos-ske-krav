package sokos.ske.krav.service

class AvstemmingService(
    private val databaseService: DatabaseService,
    private val ftpService: FtpService = FtpService()
) {

    private val STATUS_FEIL = "STATUS_FEIL"
    private val STATUS_RESEND = "STATUS_RESEND"

    fun hentAvstemmingsRapport(): String {

        val header = htmlHeader("Åpne statuser")
        val body = statusTableHeader() + statusTable(STATUS_FEIL)
        val footer = statusFooter(STATUS_FEIL)
        return "$header $body $footer"

    }

    fun hentAvstemminsRapportSomFil(): String {
        val header = "Krav-Id,Vedtaks-Id,Fagsystem-Id,Registrert,Kravkode,Hjemmelskode,Status,StatusDato\n"
        val linjer = databaseService.getAllKravForAvstemming().map {
            "${it.kravId},${it.saksnummerNAV},${it.tidspunktOpprettet},${it.kravkode},${it.kodeHjemmel},${it.status},${it.tidspunktSisteStatus}"
        }.joinToString("\n")
        return header + linjer
    }

    fun hentKravSomSkalresendes() : String {
        val header = htmlHeader("Krav som skal resendes")
        val body = statusTableHeader() + statusTable(STATUS_RESEND)
        val footer = statusFooter(STATUS_RESEND)
        return "$header $body $footer"
    }

    fun oppdaterAvstemtKravTilRapportert(kravId: Int): String {
        databaseService.updateStatusForAvstemtKravToReported(kravId)
        return hentAvstemmingsRapport()
    }

    fun visFeilFiler(): String {
        val sb: StringBuilder = StringBuilder()
        val filer = ftpService.listAllFiles(Directories.FAILED.value)
        sb.append(
            """
            <!doctype html><head><meta charset="utf-8" />
            <title>Filer som feilet</title>
            </head>
            <body><H1>Filer som har feilet i filvalidering</H1>
            <table width="50%" border="1" cellpadding="5"><tr>
            <th align="left"    >Filnavn</th>
            </tr> 
        """.trimIndent()
        )
        filer.forEach {
            if (!it.trim().equals(".") && !it.trim().equals("..")) {
                sb.append(
                    """
                    <tr><td>$it</td</tr>
            """.trimIndent()
                )
            }
        }
        sb.append(
            """
           </table>
           </body>
           </html>
        """.trimIndent()
        )
        return sb.toString()
    }

    private fun statusTable(type: String):String {
        val kravListe =
            if (STATUS_RESEND.equals(type)) databaseService.getAllKravForResending()
            else databaseService.getAllKravForAvstemming()
        if (kravListe.isEmpty()) return "<tr><td colspan=9><H2> Ingen krav i DB som har statustypen. </H2></td></tr>"
        //else return "<tr><td colspan=9><H2> Ingen krav i DB som har statustypen. </H2></td></tr>"
        val result = kravListe.map {
            val submit = """<form action ="avstemming/update/${it.kravId}" method="get">
            <input type="submit" value="Fjern fra liste">
            </form>"""

            """
            <tr><td rowspan="2">${it.kravId}</td>
            <td>${it.saksnummerNAV}</td>
            <td>${it.fagsystemId}</td>
            <td>${it.tidspunktOpprettet}</td>
            <td>${it.kravkode}</td>
            <td>${it.kodeHjemmel}</td>
            <td>${it.status}</td>
            <td>${it.tidspunktSisteStatus}</td>
            <td rowspan="2">
            ${if (STATUS_FEIL.equals(type)) submit else "" }
            </td></tr>
            <tr> ${hentFeillinjeForKravid(it.kravId.toInt())} </tr><tr/>
        """.trimIndent()
        }.joinToString("")
        return result
    }

    private fun htmlHeader(title: String) =
        """
            <!doctype html><head><meta charset="utf-8" />
            <title>$title</title>
            </head>
            <body><H1>$title</H1>
        """.trimIndent()

    private fun statusTableHeader() =
        """
            <table border="1">
            <tr>
            <th>Krav-Id</th>
            <th>Vedtaks-Id</th>
            <th>Fagsystem-Id</th>
            <th>Registrert</th>
            <th>Kravkode</th>
            <th>Hjemmelskode</th>
            <th>Status</th>
            <th>StatusDato</th>
            <th></th>
            </tr> 
        """.trimIndent()

    private fun statusFooter(type: String = STATUS_FEIL): String {
        val submit ="""
                       <form action ="status/fil" method="get">
           <p><input type="submit" value="Last ned .csv fil"></p>
           </form>

        """.trimIndent()
        val result = """
           </table>
           ${if (STATUS_FEIL.equals(type)) submit else ""}
           </body>
           </html>
        """.trimIndent()
        return result
    }

    private fun hentFeillinjeForKravid(kravid: Int): String {
        val feilmeldinger = databaseService.getErrorMessageForKravId(kravid)
        if (feilmeldinger.isEmpty()) {
            return """
            <td colspan="7"/>
        """.trimIndent()
        } else {
            return """
                <td colspan="7">"<b>Feilmelding: </b> ${feilmeldinger.first().melding}"</td>
            """.trimIndent()
        }

    }
}


