package no.nav.sokos.ske.krav.frontend

import java.time.LocalDate
import java.time.format.DateTimeFormatter

import kotlinx.html.FlowContent
import kotlinx.html.FormMethod
import kotlinx.html.InputType
import kotlinx.html.button
import kotlinx.html.classes
import kotlinx.html.form
import kotlinx.html.img
import kotlinx.html.input
import kotlinx.html.li
import kotlinx.html.table
import kotlinx.html.td
import kotlinx.html.th
import kotlinx.html.tr
import kotlinx.html.ul

import io.ktor.server.html.Template

import no.nav.sokos.ske.krav.domain.Status
import no.nav.sokos.ske.krav.service.Frontend
import no.nav.sokos.ske.krav.service.RapportService
import no.nav.sokos.ske.krav.service.RapportService.RapportObjekt

@Frontend
class AvstemmingTemplate : Template<FlowContent> {
    private val data = RapportService().kravSomSkalAvstemmes
    private val tableHeaders = RapportObjekt.headers
    private val avstemmingCSV = RapportObjekt.csvBuilder.buildCSV(data)

    private val updateURL = "/rapporter/avstemming/update"
    private val updateBtnTitle = "Sett til rapportert"
    private val csvDownloadUrl = "/rapporter/avstemming/CSVdownload"
    private val csvDownloadBtnTitle = "Last ned CSV"

    override fun FlowContent.apply() {
        table {
            tr {
                tableHeaders.forEach {
                    th { +it }
                }

                td {
                    if (data.isNotEmpty()) {
                        form {
                            action = csvDownloadUrl
                            method = FormMethod.post
                            input {
                                type = InputType.hidden
                                name = "csv"
                                value = avstemmingCSV
                            }
                            button {
                                classes = setOf("actionbtn")
                                +csvDownloadBtnTitle
                                img {
                                    classes = setOf("buttonIcon")
                                    src = "/static/FileCsv.svg"
                                }
                            }
                        }
                    }
                }
            }

            data.forEach {
                tr {
                    td { +it.kravID }
                    td { +it.filnavn }
                    td { +it.linjenummer }
                    td { +it.vedtaksId }
                    td { +it.vedtaksDato }
                    td { +it.fagsystemId }
                    td { +it.kravkode }
                    td { +it.kodeHjemmel }
                    // TODO: Status må også ha listevisning
                    td { +it.status }
                    td { +it.stonadsType.toString() }
                    td { +it.saksnummerNAV }
                    td { +it.referansenummerGammelSak }
                    td { +it.belop.toString() }
                    td { +formatPeriodeDato(it.periodeFOM) }
                    td { +formatPeriodeDato(it.periodeTOM) }
                    td {
                        classes = setOf("feilmeldinger")
                        if (it.feilmeldinger.size == 1) {
                            +it.feilmeldinger.first()
                        } else {
                            ul {
                                it.feilmeldinger.forEach {
                                    li { +it }
                                }
                            }
                        }
                    }
                    td { +it.tidspunktSisteStatus }

                    // TODO: Rapporter valideringsfeil
                    if (it.status != Status.VALIDERINGSFEIL_AV_LINJE_I_FIL.value) {
                        td {
                            form {
                                action = updateURL
                                method = FormMethod.post
                                input {
                                    type = InputType.hidden
                                    name = "kravid"
                                    value = it.kravID
                                }
                                button {
                                    classes = setOf("btn")
                                    +updateBtnTitle
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun formatPeriodeDato(dato: String): String =
        try {
            LocalDate.parse(dato, DateTimeFormatter.ofPattern("yyyyMMdd")).format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
        } catch (e: Exception) {
            dato
        }
}
