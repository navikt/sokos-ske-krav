package no.nav.sokos.ske.krav.frontend

import kotlinx.html.FlowContent
import kotlinx.html.FormMethod
import kotlinx.html.HTML
import kotlinx.html.InputType.submit
import kotlinx.html.InputType.text
import kotlinx.html.body
import kotlinx.html.classes
import kotlinx.html.dd
import kotlinx.html.dl
import kotlinx.html.dt
import kotlinx.html.form
import kotlinx.html.head
import kotlinx.html.id
import kotlinx.html.img
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.link
import kotlinx.html.p
import kotlinx.html.styleLink

import io.ktor.server.html.Template
import io.ktor.server.html.insert

import no.nav.sokos.ske.krav.domain.Krav
import no.nav.sokos.ske.krav.service.Frontend

@Frontend
class LeitEtterKravTemplate(
    val trailer: LeitEtterKravTrailer,
) : Template<HTML> {
    override fun HTML.apply() {
        head {
            styleLink("/static/styles.css")
            link {
                rel = "icon"
                href = "/static/NAV_logo_digital_White.svg"
            }
        }
        body {
            form {
                classes = setOf("leit-etter-krav-form")
                action = "/krav"
                method = FormMethod.get
                label { +"Saksnummer Nav:" }
                input {
                    type = text
                    name = "saksnummerNav"
                    id = "saksnummerNav"
                }
                input {
                    type = submit
                    value = "Søk"
                }
            }
            insert(trailer) {}
        }
    }
}

sealed interface LeitEtterKravTrailer : Template<FlowContent>

class IntentingEnda : LeitEtterKravTrailer {
    override fun FlowContent.apply() {}
}

class FantIngenting(
    val saksnummerNav: String,
) : LeitEtterKravTrailer {
    override fun FlowContent.apply() {
        p(classes = "fant-ingenting leit-etter-krav-trailer") { +"Fant ingenting på saksnummer $saksnummerNav" }
        img(classes = "fant-ingenting-bilde") {
            src = "/static/hello404.png"
            alt = "Fant ikke krav"
        }
    }
}

class FantKrav(
    val krav: Krav,
) : LeitEtterKravTrailer {
    override fun FlowContent.apply() {
        p { +"Krav" }
        dl {
            kravFelter().forEach { (etikett, verdi) ->
                dt { +etikett }
                dd { +verdi }
            }
        }
    }

    private fun kravFelter() =
        listOf(
            "Avsender" to krav.avsender,
            "Beløp" to krav.belop.toString(),
            "Beløp rente" to krav.belopRente.toString(),
            "Corr-id" to krav.corrId,
            "Enhet behandlende" to krav.enhetBehandlende,
            "Enhet bosted" to krav.enhetBosted,
            "Fagsystem-id" to krav.fagsystemId,
            "Filnavn" to krav.filnavn,
            "Fremtidig ytelse" to krav.fremtidigYtelse.toString(),
            "Gjelder-id" to krav.gjelderId,
            "Kode årsak" to krav.kodeArsak,
            "Kode hjemmel" to krav.kodeHjemmel,
            "Krav-id" to krav.kravId.toString(),
            "Kravidentifikator SKE" to krav.kravidentifikatorSKE,
            "Kravkode" to krav.kravkode,
            "Kravtype" to krav.kravtype,
            "Linjenummer" to krav.linjenummer.toString(),
            "Periode FOM" to krav.periodeFOM,
            "Periode TOM" to krav.periodeTOM,
            "Referansenummer gammel sak" to krav.referansenummerGammelSak,
            "Saksnummer NAV" to krav.saksnummerNAV,
            "Status" to krav.status.toString(),
            "Tilleggsfrist" to (krav.tilleggsfrist?.toString() ?: "Ikke satt"),
            "Tidspunkt opprettet" to krav.tidspunktOpprettet.toString(),
            "Tidspunkt sendt" to (krav.tidspunktSendt?.toString() ?: "Ikke satt"),
            "Tidspunkt siste status" to krav.tidspunktSisteStatus.toString(),
            "Transaksjonsdato" to krav.transaksjonsDato,
            "Utbetalingsdato" to krav.utbetalDato.toString(),
            "Vedtaksdato" to krav.vedtaksDato.toString(),
        ).sortedBy { it.first }
}
