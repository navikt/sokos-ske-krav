package no.nav.sokos.ske.krav.frontend

import kotlinx.html.FlowContent
import kotlinx.html.a
import kotlinx.html.li
import kotlinx.html.nav
import kotlinx.html.ol

import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.html.Template

class NavTemplate(
    private val call: ApplicationCall,
) : Template<FlowContent> {
    override fun FlowContent.apply() {
        nav {
            ol {
                li(classes = "signature") { +(call.principal<JWTPrincipal>()?.get("name") ?: "Ikke innlogget") }
                li { a(href = "/rapporter/resending") { +"Resending" } }
                li { a(href = "/rapporter/avstemming") { +"Avstemming" } }
                li { a(href = "/krav") { +"Kravsøk" } }
            }
        }
    }
}
