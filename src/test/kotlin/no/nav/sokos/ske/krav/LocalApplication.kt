package no.nav.sokos.ske.krav

import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

// gjør akkurat som Application, men får med seg test-scope for konfigurasjonens skyld
fun main() {
    embeddedServer(Netty, port = 8080, module = Application::module).start(true)
}
