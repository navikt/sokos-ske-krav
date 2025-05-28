package no.nav.sokos.ske.krav.config

import com.auth0.jwk.JwkProvider
import com.auth0.jwk.JwkProviderBuilder
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.server.application.Application
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.authentication
import io.ktor.server.auth.basic
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import java.net.URI
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}
const val AUTHENTICATION_NAME = "azureAd"
const val BASIC_AUTH_NAME = "basicAuth"

fun Application.securityConfig(
    useAuthentication: Boolean,
    azureAdProperties: PropertiesConfig.AzureAdProperties = PropertiesConfig.AzureAdProperties(),
) {
    logger.info { "Use authentication: $useAuthentication" }
    if (useAuthentication) {
        val openIdMetadata: OpenIdMetadata = wellKnowConfig(azureAdProperties.wellKnownUrl)
        val jwkProvider = cachedJwkProvider(openIdMetadata.jwksUri)

        authentication {
            jwt(AUTHENTICATION_NAME) {
                realm = PropertiesConfig.Configuration().naisAppName
                verifier(
                    jwkProvider = jwkProvider,
                    issuer = openIdMetadata.issuer,
                )
                validate { credential ->
                    try {
                        requireNotNull(credential.payload.audience) {
                            logger.info { "Auth: Missing audience in token" }
                            "Auth: Missing audience in token"
                        }
                        require(credential.payload.audience.contains(azureAdProperties.clientId)) {
                            logger.info { "Auth: Valid audience not found in claims" }
                            "Auth: Valid audience not found in claims"
                        }
                        JWTPrincipal(credential.payload)
                    } catch (e: Exception) {
                        logger.warn(e) { "Client authentication failed" }
                        null
                    }
                }
            }

            basic(BASIC_AUTH_NAME) {
                realm = "Rapport Access"
                validate { credentials ->
                    val config = PropertiesConfig.Configuration()
                    if (credentials.name == config.basicUsername && credentials.password == config.basicPassword) {
                        UserIdPrincipal(credentials.name)
                    } else {
                        null
                    }
                }
            }
        }
    }
}

private fun cachedJwkProvider(jwksUri: String): JwkProvider =
    JwkProviderBuilder(URI(jwksUri).toURL())
        .cached(10, 24, TimeUnit.HOURS) // cache up to 10 JWKs for 24 hours
        .rateLimited(10, 1, TimeUnit.MINUTES) // if not cached, only allow max 10 different keys per minute to be fetched from external provider
        .build()

@Serializable
data class OpenIdMetadata(
    @SerialName("jwks_uri") val jwksUri: String,
    @SerialName("issuer") val issuer: String,
    @SerialName("token_endpoint") val tokenEndpoint: String,
)

private fun wellKnowConfig(wellKnownUrl: String): OpenIdMetadata {
    val openIdMetadata: OpenIdMetadata by lazy {
        runBlocking { httpClient.get(wellKnownUrl).body() }
    }
    return openIdMetadata
}
