package sokos.skd.poc.maskinporten

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mu.KotlinLogging
import sokos.skd.poc.Configuration
import java.time.Instant
import java.util.*

class MaskinportenAccessTokenClient(
    private val maskinportenConfig: Configuration.MaskinportenClientConfig,
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
                !this::token.isInitialized ||  token.expiresAt.isBefore(omToMinutter) -> {
                    println("henter ny token")
                    token = AccessToken(hentAccessTokenFraProvider())
                    token.accessToken
                }

                else -> token.accessToken
            }
        }
    }

    private suspend fun hentAccessTokenFraProvider(): Token {
        println( "hentAccessToken ${maskinportenConfig.openIdConfiguration.issuer}, ${maskinportenConfig.clientId}, ${maskinportenConfig.scopes}" )
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
            secureLogger.error { "Feil fra tokenprovider, Token: $jwt, Feilmelding: $feilmelding" }
            throw ex
        }
    }
}