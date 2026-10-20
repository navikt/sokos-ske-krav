package no.nav.sokos.ske.krav.frontend

import java.time.LocalDate
import java.time.format.DateTimeFormatter

import kotlinx.html.FlowContent
import kotlinx.html.a
import kotlinx.html.classes
import kotlinx.html.li
import kotlinx.html.table
import kotlinx.html.td
import kotlinx.html.th
import kotlinx.html.tr
import kotlinx.html.ul

import io.ktor.http.encodeURLPathPart
import io.ktor.server.html.Template

import no.nav.sokos.ske.krav.service.Frontend
import no.nav.sokos.ske.krav.service.RapportService
import no.nav.sokos.ske.krav.service.RapportService.RapportObjekt

@Frontend
class ResendingPart : Template<FlowContent> {
    private val data = RapportService().kravSomSkalResendes
    private val tableHeaders = RapportObjekt.headers
    private val inputDateFormat = DateTimeFormatter.ofPattern("yyyyMMdd")
    private val outputDateFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    override fun FlowContent.apply() {
        table {
            tr {
                tableHeaders.forEach {
                    th { +it }
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
                    td { +it.status.value }
                    td { +it.stonadsType.toString() }
                    td {
                        a(href = "/krav/${it.saksnummerNAV.encodeURLPathPart()}") {
                            +it.saksnummerNAV
                        }
                    }
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
                                it.feilmeldinger.groupingBy { s -> s }.eachCount().forEach {
                                    li { +"${it.key} (${it.value})" }
                                }
                            }
                        }
                    }
                    td { +it.tidspunktSisteStatus }
                    td { +it.kravidentifikatorSKE }
                    td { +it.kravtype }
                    td { +it.belopRente.toString() }
                    td { +it.utbetaltDato }
                    td { +it.tilleggsfrist }
                    td { +it.fremtidigYtelse.toString() }
                    td { +it.gjelderId }
                }
            }
        }
    }

    private fun formatPeriodeDato(dato: String): String =
        runCatching {
            LocalDate.parse(dato, inputDateFormat).format(outputDateFormat)
        }.getOrDefault(dato)
}
