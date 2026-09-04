package no.nav.sokos.ske.krav.frontend

import kotlinx.html.HTML
import kotlinx.html.body
import kotlinx.html.head
import kotlinx.html.link
import kotlinx.html.main
import kotlinx.html.p
import kotlinx.html.styleLink

import io.ktor.server.application.ApplicationCall
import io.ktor.server.html.Template
import io.ktor.server.html.insert

class AdminPage(
    private val call: ApplicationCall,
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
            main {
                p { +"Her kan vi f.eks putte knapper for å kjøre resending med én gang" }
                p { +"Kan kanskje også putte litt info om sist resending, eller noe stats, om vi har noe lett tilgjengelig" }
            }
        }
    }
}
