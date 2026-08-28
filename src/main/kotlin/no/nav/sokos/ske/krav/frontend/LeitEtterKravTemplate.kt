package no.nav.sokos.ske.krav.frontend

import kotlinx.html.FlowContent
import kotlinx.html.FormMethod
import kotlinx.html.HTML
import kotlinx.html.body
import kotlinx.html.classes
import kotlinx.html.dd
import kotlinx.html.dl
import kotlinx.html.dt
import kotlinx.html.form
import kotlinx.html.head
import kotlinx.html.id
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
                action = "/leit-etter-krav"
                method = FormMethod.get
                label { +"Saksnummer Nav:" }
                input {
                    type = kotlinx.html.InputType.text
                    name = "saksnummerNav"
                    id = "saksnummerNav"
                }
                input {
                    type = kotlinx.html.InputType.button
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
        p { classes = setOf("fant-ingenting", "leit-etter-krav-trailer") }
        +"Fant ingenting på saksnummer $saksnummerNav"
    }
}

class FantKrav(
    val krav: Krav,
) : LeitEtterKravTrailer {
    override fun FlowContent.apply() {
        p { +"Krav" }
        dl {
            dt { +"Avsender" }
            dd { +krav.avsender }

            dt { +"kravId" }
            dd { krav.kravId }

            dt { +"saksnummerNAV" }
            dd { +krav.saksnummerNAV }

            dt { +"status" }
            dd { +"${krav.status}" }
        }
    }
}
