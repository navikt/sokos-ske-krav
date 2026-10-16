package no.nav.sokos.ske.krav.frontend

import kotlinx.html.FlowContent
import kotlinx.html.FormMethod
import kotlinx.html.form
import kotlinx.html.hiddenInput
import kotlinx.html.input
import kotlinx.html.table
import kotlinx.html.td
import kotlinx.html.th
import kotlinx.html.tr

import io.ktor.server.html.Template

import no.nav.sokos.ske.krav.service.Frontend
import no.nav.sokos.ske.krav.service.RapportService

// Egen oversikt gjør avhengige krav synlige uten å blande dem med automatisk resendbare krav.
@Frontend
class PendingRecoveryPart(
    private val data: List<RapportService.RapportObjekt> = RapportService().pendingRecoveryKrav,
) : Template<FlowContent> {
    override fun FlowContent.apply() {
        table {
            tr {
                th { +"Krav-ID" }
                th { +"Filnavn" }
                th { +"Saksnummer NAV" }
                th { +"Referanse gammel sak" }
                th { +"Status" }
                th { +"Kravtype" }
                th { +"Handling" }
            }
            data.forEach { krav ->
                tr {
                    td { +krav.kravID }
                    td { +krav.filnavn }
                    td { +krav.saksnummerNAV }
                    td { +krav.referansenummerGammelSak }
                    td { +krav.status.value }
                    td { +krav.kravtype }
                    td {
                        // Hver rad frigjøres separat for å unngå at én godkjent oppfølging også
                        // sender andre pending-operasjoner på samme sak.
                        form {
                            action = "/rapporter/pending-recovery/release"
                            method = FormMethod.post
                            hiddenInput {
                                name = "kravid"
                                value = krav.kravID
                            }
                            input {
                                type = kotlinx.html.InputType.submit
                                value = "Frigi"
                            }
                        }
                    }
                }
            }
        }
    }
}
