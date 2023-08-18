package sokos.skd.poc.maskinporten

import com.fasterxml.jackson.annotation.JsonProperty
import com.natpryce.konfig.*
import com.natpryce.konfig.ConfigurationProperties.Companion.fromOptionalFile
import com.natpryce.konfig.ConfigurationProperties.Companion.systemProperties
import com.nimbusds.jose.Algorithm
import com.nimbusds.jose.jwk.KeyUse
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.apache.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.get
import io.ktor.serialization.jackson.*
import kotlinx.coroutines.runBlocking
import org.apache.http.impl.conn.SystemDefaultRoutePlanner
import sokos.skd.poc.maskinporten.HttpClientConfig.httpClient
import java.io.File
import java.net.ProxySelector
import java.util.*

object Config {
    private val defaultProperties = ConfigurationMap(
        mapOf(
            "NAIS_APP_NAME" to "sokos-skd-poc",
            "NAIS_NAMESPACE" to "okonomi"
        )
    )

    private val localProperties = ConfigurationMap(
        mapOf(
            "application.profile" to Profile.LOCAL.toString(),
            "USE_AUTHENTICATION" to "true",
            "SKD_CLIENT_URL" to "https://api-test.sits.no/api/innkreving/innkrevingsoppdrag/v1/innkrevingsoppdrag",
            "MASKINPORTEN_CLIENT_ID" to "da2d7579-bb29-4753-a76b-6f3900d8957b",
            "MASKINPORTEN_WELL_KNOWN_URL" to "https://ver2.maskinporten.no/.well-known/oauth-authorization-server"
        )
    )

    private val devProperties = ConfigurationMap(mapOf("application.profile" to Profile.DEV.toString()))
    private val prodProperties = ConfigurationMap(mapOf("application.profile" to Profile.PROD.toString()))

    private val config = when (System.getenv("NAIS_CLUSTER_NAME") ?: System.getProperty("NAIS_CLUSTER_NAME")) {
        "dev-gcp" -> systemProperties() overriding EnvironmentVariables() overriding devProperties overriding defaultProperties
        "prod-gcp" -> systemProperties() overriding EnvironmentVariables() overriding prodProperties overriding defaultProperties
        else -> systemProperties() overriding EnvironmentVariables() overriding fromOptionalFile(File("defaults.properties")) overriding localProperties overriding defaultProperties
    }

    private fun getRSAkey(clientJwk: String?): RSAKey = clientJwk?.let { RSAKey.parse(it) } ?: generateRSAKey()

    private fun generateRSAKey() = RSAKeyGenerator(2048)
        .keyID(UUID.randomUUID().toString())
        .keyUse(KeyUse.SIGNATURE)
        .algorithm(Algorithm.parse("RS256"))
        .generate()

    operator fun get(key: String): String = config[Key(key, stringType)]
    fun getOrNull(key: String): String? = config.getOrNull(Key(key, stringType))

    data class Configuration(
        val profile: Profile = Profile.valueOf(this["application.profile"]),
        val useAuthentication: Boolean = get("USE_AUTHENTICATION").toBoolean(),
        val maskinportenConfig: MaskinportenConfig = MaskinportenConfig()
    )

    data class MaskinportenConfig(
        val wellKnownUrl: String = this["MASKINPORTEN_WELL_KNOWN_URL"],
        val clientJwk: RSAKey = getRSAkey(getOrNull("MASKINPORTEN_CLIENT_JWK")),
        val clientId: String = this["MASKINPORTEN_CLIENT_ID"]
    )

    data class Metadata(
        @JsonProperty("issuer") val issuer: String,
        @JsonProperty("jwks_uri") val jwksUri: String,
        @JsonProperty("token_endpoint") val tokenEndpoint: String
    )

    fun wellKnowConfig(wellKnownUrl: String): Metadata = runBlocking { httpClient.get(wellKnownUrl).body() }

    enum class Profile {
        LOCAL, DEV, PROD
    }
}

object HttpClientConfig {
    val httpClient = HttpClient(Apache) {
        expectSuccess = true
        install(ContentNegotiation) {
            jackson {
                serializationConfig()
            }
        }

        engine {
            customizeClient {
                setRoutePlanner(SystemDefaultRoutePlanner(ProxySelector.getDefault()))
            }
        }
    }
}

