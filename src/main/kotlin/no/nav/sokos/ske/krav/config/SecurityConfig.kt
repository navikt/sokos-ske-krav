package no.nav.sokos.ske.krav.config

import io.ktor.server.application.Application
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.authentication
import io.ktor.server.auth.basic

const val BASIC_AUTH_NAME = "basicAuth"

fun Application.securityConfig() =
    authentication {
        basic(BASIC_AUTH_NAME) {
            realm = "Rapport Access"
            validate { credentials ->
                val properties = PropertiesConfig.applicationProperties
                if (credentials.name == properties.basicUsername && credentials.password == properties.basicPassword) {
                    UserIdPrincipal(credentials.name)
                } else {
                    null
                }
            }
        }
    }
