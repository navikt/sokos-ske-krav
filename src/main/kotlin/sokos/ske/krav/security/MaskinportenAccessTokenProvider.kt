package sokos.ske.krav.security

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.http.Parameters
import io.ktor.http.isSuccess
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import mu.KotlinLogging
import sokos.ske.krav.config.PropertiesConfig
import sokos.ske.krav.util.parseTo
import java.time.Duration
import java.time.Instant
import java.util.Date
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

class MaskinportenAccessTokenProvider(
    private val maskinportenConfig: PropertiesConfig.MaskinportenClientConfig = PropertiesConfig.MaskinportenClientConfig(),
    private val client: HttpClient,
) {
    private val logger = KotlinLogging.logger {}
    private val secureLogger = KotlinLogging.logger("secureLogger")
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
                tokenCache.set(getMaskinportenToken())
            }

            tokenCache.get()!!.token
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

        return if (response.status.isSuccess()) {
            AccessToken(response.body<MaskinportenTokenResponse>())
        } else {
            logger.error("Kunne ikke hente accessToken, se sikker log for meldingen som string")
            val feilmelding = response.parseTo<TokenError>()
            secureLogger.error("Feil fra tokenprovider, Token: $jwtAssertion, Feilmelding: $feilmelding")
            throw Exception("Feil fra tokenprovider, Token: $jwtAssertion, Feilmelding: $feilmelding")
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
