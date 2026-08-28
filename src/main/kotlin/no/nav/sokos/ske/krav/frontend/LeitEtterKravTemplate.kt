package no.nav.sokos.ske.krav.frontend

import kotlinx.html.FormMethod
import kotlinx.html.HTML
import kotlinx.html.body
import kotlinx.html.classes
import kotlinx.html.form
import kotlinx.html.head
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.link
import kotlinx.html.styleLink

import io.ktor.server.html.Template

import no.nav.sokos.ske.krav.service.Frontend

@Frontend
class LeitEtterKravTemplate : Template<HTML> {
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
        }
    }
}
