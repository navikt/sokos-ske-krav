package sokos.ske.krav.service

import sokos.ske.krav.database.models.KravTable
import sokos.ske.krav.domain.Status

class AvstemmingService(
    private val databaseService: DatabaseService = DatabaseService(),
    private val ftpService: FtpService = FtpService(),
) {
    private val statusFeil = "STATUS_FEIL"
    private val statusResend = "STATUS_RESEND"

    fun hentAvstemmingsRapportSomCSVFil(): String {
        val header = "Krav-Id,Vedtaks-Id,Fagsystem-Id,Registrert,Kravkode,Hjemmelskode,Status,StatusDato\n"
        val linjer =
            databaseService.getAllKravForAvstemming().joinToString("\n") {
                "${it.kravId},${it.saksnummerNAV},${it.tidspunktOpprettet},${it.kravkode},${it.kodeHjemmel},${it.status},${it.tidspunktSisteStatus}"
            }
        return header + linjer
    }

    fun hentAvstemmingsRapport() = buildHtml("Åpne statuser", statusFeil)

    fun hentKravSomSkalresendes() = buildHtml("Krav Som Skal Resendes", statusResend)

    private fun buildHtml(
        title: String,
        status: String,
    ): String {
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
        val filer = ftpService.listFiles(Directories.FAILED)
        sb.append(
            """
            <!doctype html><head><meta charset="utf-8" />
            <title>Filer som feilet</title>
            </head>
            <body><H1>Filer som har feilet i filvalidering</H1>
            <table width="50%" border="1" cellpadding="5"><tr>
            <th align="left"    >Filnavn</th>
            </tr> 
            """.trimIndent(),
        )
        filer.forEach {
            sb.append(
                """
                <tr><td>$it</td</tr>
                """.trimIndent(),
            )
        }
        sb.append(
            """
            </table>
            </body>
            </html>
            """.trimIndent(),
        )
        return sb.toString()
    }

    private fun statusTable(type: String): String {
        val kravListe =
            if (type == statusResend) {
                databaseService.getAllKravForResending()
            } else {
                databaseService.getAllKravForAvstemming()
            }
        if (kravListe.isEmpty()) return "<tr><td colspan=9><H2> Ingen krav i DB som har statustypen. </H2></td></tr>"

        return kravListe.joinToString("") {
            val submit =
                if (type == statusFeil) {
                    """<form action ="avstemming/update/${it.kravId}" method="get">
            <input type="submit" value="Fjern fra liste">
            </form>"""
                } else {
                    ""
                }

            """
            <tr>
            <td rowspan="2">${it.kravId}</td>
            <td>${it.filnavn}</td>
            <td>${it.linjenummer}</td>
            <td>${it.saksnummerNAV}</td>
            <td>${it.fagsystemId}</td>
            <td>${it.tidspunktOpprettet}</td>
            <td>${it.kravkode}</td>
            <td>${it.kodeHjemmel}</td>
            <td>${it.status}</td>
            <td>${it.tidspunktSisteStatus}</td>
            <td rowspan="2">
            ${if (statusFeil == type) submit else "" }
            </td></tr>
            <tr> ${ hentFeillinjeForKrav(it) } </tr><tr/>
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
        <th>Filnavn</th>
        <th>Linje</th>
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
        val submit =
            if (type == statusFeil) {
                """
                <form action ="avstemming/fil" method="get">
                <p><input type="submit" value="Last ned .csv fil"></p>
                </form>
                """.trimIndent()
            } else {
                ""
            }

        return """
            </table>
            $submit
            </body>
            </html>
            """.trimIndent()
    }

    private fun hentFeillinjeForKrav(kravTable: KravTable): String {
        val feilmelding =
            if (kravTable.status == Status.VALIDERINGSFEIL_AV_LINJE_I_FIL.value) {
                databaseService
                    .getValidationMessageForKrav(kravTable)
                    .takeIf { it.isNotEmpty() }
                    ?.first()
                    ?.feilmelding
            } else {
                databaseService
                    .getFeilmeldingForKravId(kravTable.kravId)
                    .takeIf { it.isNotEmpty() }
                    ?.first()
                    ?.melding
            }
        return if (feilmelding.isNullOrBlank()) {
            """
            <td colspan="7"/>
            """.trimIndent()
        } else {
            """
            <td colspan="9">"<b>Feilmelding: </b> $feilmelding"</td>
            """.trimIndent()
        }
    }
}
