package no.nav.sokos.ske.krav

import io.kotest.core.config.AbstractProjectConfig
import io.ktor.server.config.ApplicationConfig

import no.nav.sokos.ske.krav.config.PropertiesConfig

class ProjectConfig : AbstractProjectConfig() {
    override suspend fun beforeProject() {
        PropertiesConfig.load(ApplicationConfig("application-test.conf"))
    }
}
