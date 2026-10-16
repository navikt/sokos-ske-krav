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

class NavPart(
    private val call: ApplicationCall,
) : Template<FlowContent> {
    override fun FlowContent.apply() {
        nav {
            ol {
                li(classes = "signature") { +(call.principal<JWTPrincipal>()?.get("name") ?: "Ikke innlogget") }
                li { a(href = "/rapporter/resending") { +"Resending" } }
                li { a(href = "/rapporter/avstemming") { +"Avstemming" } }
                li { a(href = "/krav") { +"Kravsøk" } }
                li { a(href = "/rapporter/resendUsendte") { +"Send usendte filer" } }
                // her kan vi legge inn link til endepunktet så kommer det en knapp øverst på siden
                // men det hadde kanskje vært fint å ha en side med noe synlig tilbakemelding? evt må vi logge masse sånn at vi kan se hva som skjer
            }
        }
    }
}
