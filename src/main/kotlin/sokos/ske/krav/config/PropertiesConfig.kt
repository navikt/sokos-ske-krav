package sokos.ske.krav.config

import com.natpryce.konfig.ConfigurationMap
import com.natpryce.konfig.ConfigurationProperties
import com.natpryce.konfig.EnvironmentVariables
import com.natpryce.konfig.Key
import com.natpryce.konfig.overriding
import com.natpryce.konfig.stringType
import com.nimbusds.jose.jwk.RSAKey
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import sokos.ske.krav.util.httpClient
import java.io.File

object PropertiesConfig {
    private val defaultProperties =
        ConfigurationMap(
            mapOf(
                "NAIS_APP_NAME" to "sokos-ske-krav",
                "NAIS_NAMESPACE" to "okonomi",
            ),
        )
    private val localDevProperties =
        ConfigurationMap(
            "APPLICATION_PROFILE" to Profile.LOCAL.toString(),
            "POSTGRES_HOST" to "dev-pg.intern.nav.no",
            "POSTGRES_PORT" to "5422",
            "SFTP_HOST_KEY_FILE_PATH" to "hostKey",
            "SFTP_PRIVATE_KEY_FILE_PATH" to "privKey",
            "SFTP_SERVER" to "10.183.32.98",
            "SFTP_PORT" to "22",
        )

    private val devProperties = ConfigurationMap(mapOf("APPLICATION_PROFILE" to Profile.DEV.toString()))
    private val prodProperties = ConfigurationMap(mapOf("APPLICATION_PROFILE" to Profile.PROD.toString()))

    private val config =
        when (System.getenv("NAIS_CLUSTER_NAME") ?: System.getProperty("NAIS_CLUSTER_NAME")) {
            "dev-fss" -> ConfigurationProperties.systemProperties() overriding EnvironmentVariables() overriding devProperties overriding defaultProperties
            "prod-fss" -> ConfigurationProperties.systemProperties() overriding EnvironmentVariables() overriding prodProperties overriding defaultProperties
            else ->
                ConfigurationProperties.systemProperties() overriding EnvironmentVariables() overriding
                    ConfigurationProperties.fromOptionalFile(
                        File("defaults.properties"),
                    ) overriding localDevProperties overriding defaultProperties
        }

    enum class Profile {
        LOCAL,
        DEV,
        PROD,
    }

    fun isLocal() = Configuration().profile == Profile.LOCAL

    operator fun get(key: String): String = config[Key(key, stringType)]

    fun getOrEmpty(key: String): String = config.getOrElse(Key(key, stringType), "")

    data class Configuration(
        val naisAppName: String = get("NAIS_APP_NAME"),
        val profile: Profile = Profile.valueOf(this["APPLICATION_PROFILE"]),
    )

    data class SftpProperties(
        val host: String = getOrEmpty("SFTP_SERVER"),
        val username: String = getOrEmpty("SKE_SFTP_USERNAME").trim(),
        val privateKeyPassword: String = getOrEmpty("SKE_SFTP_PASSWORD").trim(),
        val privateKey: String = getOrEmpty("SFTP_PRIVATE_KEY_FILE_PATH"),
        val port: Int = getOrEmpty("SFTP_PORT").toInt(),
    )

    data class MaskinportenClientConfig(
        val clientId: String = getOrEmpty("MASKINPORTEN_CLIENT_ID"),
        val authorityEndpoint: String = getOrEmpty("MASKINPORTEN_WELL_KNOWN_URL"),
        val rsaKey: RSAKey? = RSAKey.parse(getOrEmpty("MASKINPORTEN_CLIENT_JWK")),
        val scopes: String = getOrEmpty("MASKINPORTEN_SCOPES"),
    ) : JwtConfig(authorityEndpoint)

    @Serializable
    data class OpenIdConfiguration(
        @SerialName("jwks_uri") val jwksUri: String,
        @SerialName("issuer") val issuer: String,
        @SerialName("token_endpoint") val tokenEndpoint: String,
    )

    data class SKEConfig(
        val skeRestUrl: String = getOrEmpty("SKE_REST_URL"),
    )

    data class PostgresConfig(
        val host: String = getOrEmpty("POSTGRES_HOST"),
        val port: String = getOrEmpty("POSTGRES_PORT"),
        val name: String = getOrEmpty("POSTGRES_NAME"),
        val username: String = getOrEmpty("POSTGRES_USERNAME").trim(),
        val password: String = getOrEmpty("POSTGRES_PASSWORD").trim(),
        val vaultMountPath: String = getOrEmpty("VAULT_MOUNTPATH"),
    ) {
        val adminUser = "$name-admin"
        val user = "$name-user"
    }

    data class TimerConfig(
        val useTimer: Boolean = config.getOrElse(Key("USE_TIMER", stringType), "false").toBoolean(),
        val initialDelay: Long = config.getOrElse(Key("TIMER_INITIAL_DELAY", stringType), "500").toLong(),
        val intervalPeriod: Long = config.getOrElse(Key("TIMER_INTERVAL_PERIOD", stringType), "3600000").toLong()
    )

    open class JwtConfig(
        private val wellKnownUrl: String,
    ) {
        val openIdConfiguration: OpenIdConfiguration by lazy {
            runBlocking {
                httpClient.get(wellKnownUrl).body()
            }
        }
    }
}