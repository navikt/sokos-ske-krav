package sokos.ske.krav.maskinporten

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mu.KotlinLogging
import sokos.ske.krav.config.PropertiesConfig
import java.time.Instant
import java.util.Date

class MaskinportenAccessTokenClient(
    private val maskinportenConfig: PropertiesConfig.MaskinportenClientConfig,
    private val client: HttpClient,
) {
    private val logger = KotlinLogging.logger {}
    private val secureLogger = KotlinLogging.logger("secureLogger")
    private val mutex = Mutex()

    @Volatile
    private lateinit var token: AccessToken

    suspend fun hentAccessToken(): String {
        val omToMinutter = Instant.now().plusSeconds(120L)
        return mutex.withLock {
            when {
                !this::token.isInitialized || token.expiresAt.isBefore(omToMinutter) -> {
                    token = AccessToken(hentAccessTokenFraProvider())
                    token.accessToken
                }

                else -> {
                    token.accessToken}
            }
        }
    }

    private suspend fun hentAccessTokenFraProvider(): Token {
        val jwt = JWT.create()
            .withAudience(maskinportenConfig.openIdConfiguration.issuer)
            .withIssuer(maskinportenConfig.clientId)
            .withClaim("scope", maskinportenConfig.scopes)
            .withExpiresAt(Date(System.currentTimeMillis() + 120000))
            .withIssuedAt(Date())
            .withKeyId(maskinportenConfig.rsaKey?.keyID)
            .sign(Algorithm.RSA256(null, maskinportenConfig.rsaKey?.toRSAPrivateKey()))
        val response = client.post(maskinportenConfig.openIdConfiguration.tokenEndpoint) {
            accept(ContentType.Application.Json)
            contentType(ContentType.Application.FormUrlEncoded)
            method = HttpMethod.Post
            setBody("grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=$jwt")
        }

        return try {
            response.body()
        } catch (ex: Exception) {
            logger.error { "Kunne ikke lese accessToken, se sikker log for meldingen som string" }
            val feilmelding = response.bodyAsText()
            println(feilmelding)
            secureLogger.error { "Feil fra tokenprovider, Token: $jwt, Feilmelding: $feilmelding" }
            throw ex
        }
    }
}