package no.nav.sokos.ske.krav.security

import java.time.Duration
import java.time.Instant
import java.util.Date
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import mu.KotlinLogging

import no.nav.sokos.ske.krav.config.MaskinportenClientConfig
import no.nav.sokos.ske.krav.config.PropertiesConfig
import no.nav.sokos.ske.krav.config.TEAM_LOGS_MARKER
import no.nav.sokos.ske.krav.util.decodeTo

class MaskinportenAccessTokenProvider(
    private val maskinportenConfig: MaskinportenClientConfig = PropertiesConfig.maskinportenClientProperties,
    private val client: HttpClient,
) {
    private val logger = KotlinLogging.logger {}
    private val mutex = Mutex()

    private val timeLimit = Duration.ofSeconds(60)

    private data class AccessToken(
        val token: String,
        val expiresAt: Instant,
    ) {
        constructor(maskinportenTokenResponse: MaskinportenTokenResponse) :
            this(maskinportenTokenResponse.accessToken, Instant.now().plusSeconds(maskinportenTokenResponse.expiresIn))
    }

    private val tokenCache = AtomicReference<AccessToken?>(null)

    suspend fun getAccessToken(): String =
        mutex.withLock {
            val nowPlusLimit = Instant.now().plus(timeLimit)
            val cachedToken = tokenCache.get()

            if (cachedToken == null || cachedToken.expiresAt < nowPlusLimit) {
                val newToken = getMaskinportenToken()
                tokenCache.set(newToken)
                newToken.token
            } else {
                cachedToken.token
            }
        }

    private suspend fun getMaskinportenToken(): AccessToken {
        val openIdConfiguration = client.get(maskinportenConfig.wellKnownUrl).body<OpenIdConfiguration>()
        val jwtAssertion = createJwtAssertion(openIdConfiguration.issuer)
        val response =
            client
                .submitForm(
                    url = openIdConfiguration.tokenEndpoint,
                    formParameters =
                        Parameters.build {
                            append("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer")
                            append("assertion", jwtAssertion)
                        },
                )

        val responseBody = response.bodyAsText()
        val tokenResponse = responseBody.decodeTo<MaskinportenTokenResponse>()
        return if (tokenResponse != null) {
            AccessToken(tokenResponse)
        } else {
            logger.error("Kunne ikke hente accessToken,")
            val feilmelding = responseBody.decodeTo<TokenError>()
            logger.error(marker = TEAM_LOGS_MARKER) {
                "Feil fra tokenprovider, Feilmelding: $feilmelding"
            }
            throw Exception("Feil fra tokenprovider: $feilmelding")
        }
    }

    private fun createJwtAssertion(issuer: String): String =
        JWT
            .create()
            .withIssuer(maskinportenConfig.clientId)
            .withAudience(issuer)
            .withClaim("scope", maskinportenConfig.scopes)
            .withExpiresAt(
                Date(
                    Instant
                        .now()
                        .plus(timeLimit)
                        .toEpochMilli(),
                ),
            ).withIssuedAt(Date())
            .withKeyId(maskinportenConfig.rsaKey?.keyID)
            .withJWTId(UUID.randomUUID().toString())
            .sign(Algorithm.RSA256(null, maskinportenConfig.rsaKey?.toRSAPrivateKey()))

    @Serializable
    private data class MaskinportenTokenResponse(
        @SerialName("access_token") val accessToken: String,
        @SerialName("expires_in")
        val expiresIn: Long,
        val token_type: String,
    )

    @Serializable
    private data class TokenError(
        @SerialName("error") val error: String,
        @SerialName("error_description") val errorDescription: String,
        @SerialName("error_uri") val errorUri: String? = null,
    )

    @Serializable
    private data class OpenIdConfiguration(
        @SerialName("jwks_uri") val jwksUri: String,
        @SerialName("issuer") val issuer: String,
        @SerialName("token_endpoint") val tokenEndpoint: String,
    )
}
