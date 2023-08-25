package sokos.skd.poc

import com.fasterxml.jackson.annotation.JsonProperty
import com.nimbusds.jose.jwk.RSAKey
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

data class Configuration(
    val skdRestUrl: String = readProperty("SKD_REST_URL", ""),
    val maskinportenClientConfig: MaskinportenClientConfig = MaskinportenClientConfig(),
    val dbConfig: DbConfig = DbConfig()
) {
    data class MaskinportenClientConfig(
        val clientId: String = readProperty("MASKINPORTEN_CLIENT_ID", ""),
        val authorityEndpoint: String = readProperty("MASKINPORTEN_WELL_KNOWN_URL", ""),
        val rsaKey: RSAKey? = RSAKey.parse(readProperty("MASKINPORTEN_CLIENT_JWK", "")),
        val scopes: String = readProperty("MASKINPORTEN_SCOPES", ""),
    ) : JwtConfig(authorityEndpoint)


    data class OpenIdConfiguration(
        @JsonProperty("jwks_uri") val jwksUri: String,
        @JsonProperty("issuer") val issuer: String,
        @JsonProperty("token_endpoint") val tokenEndpoint: String,
    )

    open class JwtConfig(private val wellKnownUrl: String) {
        val openIdConfiguration: OpenIdConfiguration by lazy {
            runBlocking {
                defaultHttpClient.get(wellKnownUrl).body()
            }
        }
    }

    data class DbConfig(
        val dbHost: String = readProperty(""),
        val dbPort: String = readProperty(""),
        val dbName: String = readProperty(""),
        val dbUserName: String = readProperty(""),
        val dbPassword: String = readProperty("")
    ){
        val jdbcUrl: String = "jdbc:postgresql://$dbHost:$dbPort/$dbName"
    }


}

fun readProperty(name: String, default: String? = null) =
    System.getenv(name)
        ?: System.getProperty(name)
        ?: default.takeIf { it != null }?.also { logger.info("Bruker default verdi for property $name") }
        ?: throw RuntimeException("Mandatory property '$name' was not found")
