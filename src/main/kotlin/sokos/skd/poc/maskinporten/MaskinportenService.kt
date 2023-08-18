package sokos.skd.poc.maskinporten

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.fasterxml.jackson.annotation.JsonAlias
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mu.KotlinLogging
import sokos.skd.poc.Utils.retry
import java.time.Instant
import java.util.*

internal const val MAX_EXPIRY_SECONDS = 120L
internal const val CLAIMS_SCOPE = "scope"
internal const val SCOPES = "skatteetaten:innkrevingsopdrag"

internal const val CLIENT_ID = "da2d7579-bb29-4753-a76b-6f3900d8957b"

private val logger = KotlinLogging.logger {}
private const val GRANT_TYPE_VALUE = "urn:ietf:params:oauth:grant-type:jwt-bearer"
private const val GRANT_TYPE = "grant_type"
private const val ASSERTION = "assertion"

class MaskinportenService(private val maskinportenConfig: Config.MaskinportenConfig) {
    private val mutex = Mutex()

    @Volatile
    private var token: AccessToken = runBlocking {
        AccessToken(requestToken())
    }

    suspend fun getToken(): String {
        val expires = Instant.now().plusSeconds(120L)
        return mutex.withLock {
            if (token.expiresAt.isBefore(expires)) {
                token = AccessToken(requestToken())
            }
            token.accessToken
        }
    }

    private suspend fun requestToken(): MaskinportenAccessTokenResponse {
        logger.info("Request new maskinporten accessToken.")
        val metadata: Config.Metadata = Config.wellKnowConfig(maskinportenConfig.wellKnownUrl)
        return retry {
            val jwt = generateJWT(metadata)
            val response = HttpClientConfig.httpClient.submitForm(
                url = metadata.tokenEndpoint,
                formParameters = parametersOf(
                    GRANT_TYPE to listOf(GRANT_TYPE_VALUE),
                    ASSERTION to listOf(jwt)
                )
            )

            println("well known: ${maskinportenConfig.wellKnownUrl}")
            println("token endpoint: ${metadata.tokenEndpoint}")
            println("response: ${response.status}")
            response.body()
        }
    }

    private fun generateJWT(metadata: Config.Metadata): String {
        val now = Instant.now()
        return JWT.create()
            .withSubject(maskinportenConfig.clientId)
            .withIssuer(maskinportenConfig.clientId)
            .withAudience(metadata.issuer)
            .withClaim(CLAIMS_SCOPE, SCOPES)
            .withIssuedAt(Date.from(now))
            .withExpiresAt(Date.from(now.plusSeconds(MAX_EXPIRY_SECONDS)))
            .withKeyId(maskinportenConfig.clientJwk.keyID)
            .withJWTId(UUID.randomUUID().toString())
            .sign(Algorithm.RSA256(null, maskinportenConfig.clientJwk.toRSAPrivateKey()))
    }
}

data class MaskinportenAccessTokenResponse(
    @JsonAlias("access_token") val accessToken: String,
    @JsonAlias("expires_in") val expiresIn: Long,
    @JsonAlias("scope") val scope: String = "",
    @JsonAlias("audience") val audience: String = "",
    @JsonAlias("token_type") val tokenType: String = ""
)

private data class AccessToken(
    val accessToken: String,
    val expiresAt: Instant
) {
    constructor(tokenResponse: MaskinportenAccessTokenResponse) : this(
        accessToken = tokenResponse.accessToken,
        expiresAt = Instant.now().plusSeconds(tokenResponse.expiresIn)
    )
}
