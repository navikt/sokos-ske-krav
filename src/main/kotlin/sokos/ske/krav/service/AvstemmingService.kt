package sokos.ske.krav.service

class AvstemmingService(
    private val databaseService: DatabaseService,
    private val ftpService: FtpService = FtpService()
) {

    private val statusFeil = "STATUS_FEIL"
    private val statusResend = "STATUS_RESEND"


    fun hentAvstemminsRapportSomFil(): String {
        val header = "Krav-Id,Vedtaks-Id,Fagsystem-Id,Registrert,Kravkode,Hjemmelskode,Status,StatusDato\n"
        val linjer = databaseService.getAllKravForAvstemming().joinToString("\n") {
            "${it.kravId},${it.saksnummerNAV},${it.tidspunktOpprettet},${it.kravkode},${it.kodeHjemmel},${it.status},${it.tidspunktSisteStatus}"
        }
        return header + linjer
    }


    fun hentAvstemmingsRapport() = buildHtml("Åpne statuser", statusFeil)
    fun hentKravSomSkalresendes() = buildHtml("Krav Som Skal Resendes", statusResend)


    private fun buildHtml(title: String, status: String): String {
        val header = htmlHeader(title)
        val body = statusTableHeader + statusTable(status)
        val footer = statusFooter(status)
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
            if (it.trim() != "." && it.trim() != "..") {
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

    private fun statusTable(type: String): String {
        val kravListe =
            if (type == statusResend) databaseService.getAllKravForResending()
            else databaseService.getAllKravForAvstemming()
        if (kravListe.isEmpty()) return "<tr><td colspan=9><H2> Ingen krav i DB som har statustypen. </H2></td></tr>"

        return kravListe.joinToString("") {
            val submit = if (type == statusFeil)
                """<form action ="avstemming/update/${it.kravId}" method="get">
            <input type="submit" value="Fjern fra liste">
            </form>""" else ""

            """
            <tr>
            <td rowspan="2">${it.kravId}</td>
            <td>${it.saksnummerNAV}</td>
            <td>${it.fagsystemId}</td>
            <td>${it.tidspunktOpprettet}</td>
            <td>${it.kravkode}</td>
            <td>${it.kodeHjemmel}</td>
            <td>${it.status}</td>
            <td>${it.tidspunktSisteStatus}</td>
            <td rowspan="2">
            $submit
            </td></tr>
            <tr> ${hentFeillinjeForKravid(it.kravId.toInt())} </tr><tr/>
        """.trimIndent()
        }
    }

    private fun htmlHeader(title: String) =
        """
            <!doctype html><head><meta charset="utf-8" />
            <title>$title</title>
            </head>
            <body><H1>$title</H1>
        """.trimIndent()

    private val statusTableHeader =
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

    private fun statusFooter(type: String = statusFeil): String {
        val submit = if (type == statusFeil)
            """<form action ="status/fil" method="get">
           <p><input type="submit" value="Last ned .csv fil"></p>
           </form>
        """.trimIndent()
        else ""

        return """
           </table>
           $submit
           </body>
           </html>
        """.trimIndent()

    }

    private fun hentFeillinjeForKravid(kravid: Int): String {
        val feilmeldinger = databaseService.getErrorMessageForKravId(kravid)
        return if (feilmeldinger.isEmpty()) {
            """<td colspan="7"/>""".trimIndent()
        } else {
            """<td colspan="7">"<b>Feilmelding: </b> ${feilmeldinger.first().melding}"</td>""".trimIndent()
        }

    }
}


