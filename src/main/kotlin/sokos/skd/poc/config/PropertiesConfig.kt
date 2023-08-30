package sokos.skd.poc.config

import com.fasterxml.jackson.annotation.JsonProperty
import com.natpryce.konfig.*
import com.nimbusds.jose.jwk.RSAKey
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.coroutines.runBlocking

import sokos.skd.poc.defaultHttpClient
import java.io.File

object PropertiesConfig {

private val defaultProperties = ConfigurationMap(
    mapOf(
        "NAIS_APP_NAME" to "sokos-skd-poc",
        "NAIS_NAMESPACE" to "okonomi",
    )
)
    private val localDevProperties = ConfigurationMap(
        "APPLICATION_PROFILE" to Profile.LOCAL.toString(),
        "DB_HOST" to "host",
        "DB_PORT" to "123",
        "DB_NAME" to "name",
        "DB_USERNAME" to "username",
        "DB_PASSWORD" to "password",
        "HIKARI_TEST_TABLE" to "HIKARI_TEST_TABLE"
    )
    private val devProperties = ConfigurationMap(mapOf("APPLICATION_PROFILE" to Profile.DEV.toString()))
    private val prodProperties = ConfigurationMap(mapOf("APPLICATION_PROFILE" to Profile.PROD.toString()))

    private val config = when (System.getenv("NAIS_CLUSTER_NAME") ?: System.getProperty("NAIS_CLUSTER_NAME")) {
        "dev-fss" -> ConfigurationProperties.systemProperties() overriding EnvironmentVariables() overriding devProperties overriding defaultProperties
        "prod-fss" -> ConfigurationProperties.systemProperties() overriding EnvironmentVariables() overriding prodProperties overriding defaultProperties
        else ->
            ConfigurationProperties.systemProperties() overriding EnvironmentVariables() overriding ConfigurationProperties.fromOptionalFile(File("defaults.properties")) overriding localDevProperties overriding defaultProperties
    }


    enum class Profile {
        LOCAL, DEV, PROD
    }
    operator fun get(key: String): String = config[Key(key, stringType)]

    data class FtpConfig(
        val server:String = get("FTP_SERVER"),
        val username:String = get("FTP_USERNAME"),
        val password:String = get("FTP_PASSWORD"),
        val homeDirectory:String = get("FTP_DIRECTORY"),
        val port:Int = get("FTP_PORT").toInt()
    )

    data class MaskinportenClientConfig(
        val clientId: String = get("MASKINPORTEN_CLIENT_ID"),
        val authorityEndpoint: String = get("MASKINPORTEN_WELL_KNOWN_URL"),
        val rsaKey: RSAKey? = RSAKey.parse(get("MASKINPORTEN_CLIENT_JWK")),
        val scopes: String = get("MASKINPORTEN_SCOPES"),
    ): JwtConfig(authorityEndpoint)

    data class OpenIdConfiguration(
        @JsonProperty("jwks_uri") val jwksUri: String,
        @JsonProperty("issuer") val issuer: String,
        @JsonProperty("token_endpoint") val tokenEndpoint: String,
    )
    data class SKEConfig(
        val skeRestUrl: String = get("SKD_REST_URL")
    )

    data class DbConfig(
/*        val host: String = get("DB_HOST"),
        val port: Int = get("DB_PORT").toInt(),
        val name: String = get("DB_NAME"),
        val username: String = get("DB_USERNAME"),
        val password: String = get("DB_PASSWORD"),
        val testTable: String = get("HIKARI_TEST_TABLE"),   */
        val host: String = "",
        val port: Int = 0,
        val name: String = "",
        val username: String = "",
        val password: String = "",
        val testTable: String = "",
        val jdbcUrl: String = "jdbc:postgresql://$host:$port/$name"
    )

    open class JwtConfig(private val wellKnownUrl: String) {
        val openIdConfiguration: OpenIdConfiguration by lazy {
            runBlocking {
                defaultHttpClient.get(wellKnownUrl).body()
            }
        }
    }
}