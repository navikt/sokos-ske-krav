package no.nav.sokos.ske.krav.frontend

import kotlinx.html.FlowContent
import kotlinx.html.FormMethod
import kotlinx.html.HTML
import kotlinx.html.InputType.submit
import kotlinx.html.InputType.text
import kotlinx.html.body
import kotlinx.html.classes
import kotlinx.html.dd
import kotlinx.html.div
import kotlinx.html.dl
import kotlinx.html.dt
import kotlinx.html.form
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.head
import kotlinx.html.id
import kotlinx.html.img
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.link
import kotlinx.html.p
import kotlinx.html.styleLink

import io.ktor.server.application.ApplicationCall
import io.ktor.server.html.Template
import io.ktor.server.html.insert

import no.nav.sokos.ske.krav.domain.Krav
import no.nav.sokos.ske.krav.service.Frontend

@Frontend
class LeitEtterKravPage(
    val trailer: LeitEtterKravTrailer,
    val call: ApplicationCall,
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
            insert(NavPart(call)) {}
            div {
                classes = setOf("table-krav")
                div {
                    classes = setOf("header")
                    img {
                        classes = setOf("header-logo")
                        src = "/static/NAV_logo_digital_White.svg"
                    }
                    h1 {
                        +"Krav"
                    }
                }

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
}

sealed interface LeitEtterKravTrailer : Template<FlowContent>

object IngentingEnda : LeitEtterKravTrailer {
    override fun FlowContent.apply() {}
}

class FantIngenting(
    val saksnummerNav: String,
) : LeitEtterKravTrailer {
    override fun FlowContent.apply() {
        p(classes = "fant-ingenting") { +"Fant ingenting på saksnummer $saksnummerNav" }
        img(classes = "fant-ingenting-bilde") {
            src = "/static/hello404.png"
            alt = "Fant ikke krav"
        }
    }
}

class FantKrav(
    val krav: List<Krav>,
) : LeitEtterKravTrailer {
    override fun FlowContent.apply() {
        h2 { +"Krav for sak ${krav.first().saksnummerNAV}" }
        dl {
            classes = setOf("krav-def")
            kravFelter().forEach { (etikett, verdi) ->
                div {
                    this@dl.dt { +etikett }
                    for (v in verdi) {
                        this@dl.dd { +v }
                    }
                }
            }
        }
    }

    private fun kravFelter() =
        listOf(
            "Avsender" to krav.map(Krav::avsender),
            "Beløp" to krav.map { it.belop.toString() },
            "Beløp rente" to krav.map { it.belopRente.toString() },
            "Corr-id" to krav.map(Krav::corrId),
            "Enhet behandlende" to krav.map(Krav::enhetBehandlende),
            "Enhet bosted" to krav.map(Krav::enhetBosted),
            "Fagsystem-id" to krav.map(Krav::fagsystemId),
            "Filnavn" to krav.map(Krav::filnavn),
            "Fremtidig ytelse" to krav.map { it.fremtidigYtelse.toString() },
            "Gjelder-id" to krav.map(Krav::gjelderId),
            "Kode årsak" to krav.map(Krav::kodeArsak),
            "Kode hjemmel" to krav.map(Krav::kodeHjemmel),
            "Krav-id" to krav.map { it.kravId.toString() },
            "Kravidentifikator SKE" to krav.map(Krav::kravidentifikatorSKE),
            "Kravkode" to krav.map(Krav::kravkode),
            "Kravtype" to krav.map(Krav::kravtype),
            "Linjenummer" to krav.map { it.linjenummer.toString() },
            "Periode FOM" to krav.map(Krav::periodeFOM),
            "Periode TOM" to krav.map(Krav::periodeTOM),
            "Referansenummer gammel sak" to krav.map(Krav::referansenummerGammelSak),
            "Saksnummer NAV" to krav.map(Krav::saksnummerNAV),
            "Status" to krav.map { it.status.toString() },
            "Tilleggsfrist" to (krav.map { it.tilleggsfrist?.toString() ?: "Ikke satt" }),
            "Tidspunkt opprettet" to krav.map { it.tidspunktOpprettet.toString() },
            "Tidspunkt sendt" to (krav.map { it.tidspunktSendt?.toString() ?: "Ikke satt" }),
            "Tidspunkt siste status" to krav.map { it.tidspunktSisteStatus.toString() },
            "Transaksjonsdato" to krav.map(Krav::transaksjonsDato),
            "Utbetalingsdato" to krav.map { it.utbetalDato.toString() },
            "Vedtaksdato" to krav.map { it.vedtaksDato.toString() },
        ).sortedBy { it.first }
}
