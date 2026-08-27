package no.nav.sokos.ske.krav.config

import java.net.URI

import com.auth0.jwk.JwkProviderBuilder
import io.ktor.server.application.Application
import io.ktor.server.auth.authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt

fun Application.securityConfig() =
    authentication {
        jwt {
            val jwkProvider = JwkProviderBuilder(URI(PropertiesConfig.azureProperties.jwksUri).toURL()).build()
            verifier(jwkProvider, PropertiesConfig.azureProperties.configIssuer) {
                withAudience(PropertiesConfig.azureProperties.clientId)
                withClaimPresence("NAVident")
                withClaimPresence("preferred_username")
                withClaimPresence("name")
            }
            validate { credentials -> JWTPrincipal(credentials.payload) }
        }
    }
