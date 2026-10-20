package no.nav.sokos.ske.krav.frontend

import kotlinx.html.FormMethod
import kotlinx.html.HTML
import kotlinx.html.InputType
import kotlinx.html.body
import kotlinx.html.classes
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.h1
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

import no.nav.sokos.ske.krav.service.Frontend

@Frontend
class SendUsendtePage(
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
                        +"Send usendte filer"
                    }
                }
                div {
                    p {
                        +"Resending vil ta flere minutter, følg med i loggene for å se framdriften !"
                    }
                }

                form {
                    classes = setOf("leit-etter-krav-form")
                    action = "/rapporter/resendUsendte/sendUnsent"
                    method = FormMethod.post
                    label { +"Max antall filer (5)  :" }
                    input {
                        type = InputType.text
                        name = "filer"
                        id = "filer"
                        placeholder = "antall filer"
                    }
                    input {
                        type = InputType.submit
                        value = "Send usendte filer"
                    }
                }
            }
        }
    }
}
