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

import no.nav.sokos.ske.krav.config.isTestkonsollEnabled

class NavPart(
    private val call: ApplicationCall,
    private val showTestkonsoll: Boolean = isTestkonsollEnabled(),
) : Template<FlowContent> {
    override fun FlowContent.apply() {
        nav {
            ol {
                li(classes = "signature") { +(call.principal<JWTPrincipal>()?.get("name") ?: "Ikke innlogget") }
                li { a(href = "/rapporter/resending") { +"Resending" } }
                li { a(href = "/rapporter/avstemming") { +"Avstemming" } }
                li { a(href = "/rapporter/avventende-oppryddingskrav") { +"Krav som venter på opprydding" } }
                if (showTestkonsoll) {
                    li { a(href = "/testing") { +"Testkonsoll" } }
                }
                li { a(href = "/krav") { +"Kravsøk" } }
                li { a(href = "/krav/filnavn") { +"Filnavnsøk" } }
            }
        }
    }
}
