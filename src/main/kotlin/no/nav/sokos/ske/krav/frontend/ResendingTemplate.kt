package no.nav.sokos.ske.krav.frontend

import kotlinx.html.FlowContent
import kotlinx.html.table
import kotlinx.html.td
import kotlinx.html.th
import kotlinx.html.tr

import io.ktor.server.html.Template

import no.nav.sokos.ske.krav.service.Frontend
import no.nav.sokos.ske.krav.service.RapportService
import no.nav.sokos.ske.krav.service.RapportService.RapportObjekt

@Frontend
class ResendingTemplate : Template<FlowContent> {
    private val data = RapportService().kravSomSkalResendes
    private val tableHeaders = RapportObjekt.headers

    override fun FlowContent.apply() {
        table {
            tr {
                tableHeaders.forEach {
                    th { +it }
                }
            }

            data.forEach {
                tr {
                    td { +it.kravId }
                    td { +it.filnavn }
                    td { +it.linjenummer }
                    td { +it.vedtaksId }
                    td { +it.vedtaksDato }
                    td { +it.fagsystemId }
                    td { +it.kravkode }
                    td { +it.kodeHjemmel }
                    td { +it.status }
                    td { +it.tidspunktSisteStatus }
                }
            }
        }
    }
}
