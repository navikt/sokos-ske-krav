package no.nav.sokos.ske.krav.frontend

import kotlinx.html.FlowContent
import kotlinx.html.HTML
import kotlinx.html.body
import kotlinx.html.classes
import kotlinx.html.div
import kotlinx.html.h1
import kotlinx.html.head
import kotlinx.html.img
import kotlinx.html.link
import kotlinx.html.styleLink

import io.ktor.server.html.Placeholder
import io.ktor.server.html.Template
import io.ktor.server.html.TemplatePlaceholder
import io.ktor.server.html.insert

import no.nav.sokos.ske.krav.service.Frontend
import no.nav.sokos.ske.krav.service.RapportType

@Frontend
class RapportTemplate(
    private val type: RapportType,
) : Template<HTML> {
    val title = Placeholder<FlowContent>()
    val avstemmingContent = TemplatePlaceholder<AvstemmingTemplate>()
    val resendingContent = TemplatePlaceholder<ResendingTemplate>()

    override fun HTML.apply() {
        head {
            styleLink("/static/styles.css")
            link {
                rel = "icon"
                href = "/static/NAV_logo_digital_White.svg"
            }
        }
        body {
            div {
                classes = setOf("table-krav")
                div {
                    classes = setOf("header")
                    img {
                        classes = setOf("header-logo")
                        src = "/static/NAV_logo_digital_White.svg"
                    }
                    h1 {
                        insert(title)
                    }
                }

                when (type) {
                    RapportType.AVSTEMMING -> insert(AvstemmingTemplate(), avstemmingContent)
                    RapportType.RESENDING -> insert(ResendingTemplate(), resendingContent)
                }
            }
        }
    }
}
