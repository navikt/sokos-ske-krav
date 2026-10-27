package no.nav.sokos.ske.krav.frontend

import kotlinx.html.FormEncType
import kotlinx.html.FormMethod
import kotlinx.html.HTML
import kotlinx.html.body
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.fileInput
import kotlinx.html.form
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.head
import kotlinx.html.link
import kotlinx.html.main
import kotlinx.html.p
import kotlinx.html.styleLink

import io.ktor.server.application.ApplicationCall
import io.ktor.server.html.Template
import io.ktor.server.html.insert

import no.nav.sokos.ske.krav.config.isTestkonsollEnabled

class TestkonsollPage(
    private val call: ApplicationCall,
    private val feedback: String?,
    private val showTestkonsoll: Boolean = isTestkonsollEnabled(),
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
            insert(NavPart(call, showTestkonsoll)) {}
            main(classes = "testkonsoll") {
                h1 { +"Manuell testkonsoll" }
                p(
                    classes = "testkonsoll-ingress",
                ) { +"Kjør avgrensede driftssteg i dev. Behandle krav følger feature togglene, mens Hent mottaksstatus kjører direkte. Handlingene erstatter ikke planlagt kjøring." }
                feedback?.let { message ->
                    div(classes = "testkonsoll-feedback") {
                        h2 { +"Resultat" }
                        p { +message }
                    }
                }
                div(classes = "testkonsoll-actions") {
                    uploadAction()
                    action("Behandle krav", "Kjører tjenestens ordinære behandling. Innlesing, resending og utsending avhenger av feature togglene.", "/testing/behandle-krav")
                    action("Hent mottaksstatus", "Henter mottaksstatus direkte for krav som venter på status fra SKE, uavhengig av feature togglen.", "/testing/hent-mottaksstatus")
                }
            }
        }
    }

    private fun kotlinx.html.FlowContent.uploadAction() {
        div(classes = "testkonsoll-action") {
            h2 { +"Last opp fil" }
            p { +"Laster en .txt-fil direkte til SFTP-serverens /inbound. Filen behandles først når du velger Les inn filer." }
            form {
                action = "/testing/last-opp-fil"
                method = FormMethod.post
                encType = FormEncType.multipartFormData
                fileInput {
                    name = "fil"
                    accept = ".txt,text/plain"
                    required = true
                }
                button(classes = "btn") { +"Last opp fil" }
            }
        }
    }

    private fun kotlinx.html.FlowContent.action(
        title: String,
        description: String,
        actionPath: String,
    ) {
        div(classes = "testkonsoll-action") {
            h2 { +title }
            p { +description }
            form {
                action = actionPath
                method = FormMethod.post
                button(classes = "btn") { +title }
            }
        }
    }
}
