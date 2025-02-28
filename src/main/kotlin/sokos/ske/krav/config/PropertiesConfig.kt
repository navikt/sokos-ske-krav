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
import mu.KotlinLogging
import sokos.ske.krav.util.httpClient
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

val secureLogger = KotlinLogging.logger("secureLogger")

object PropertiesConfig {
    private val defaultProperties =
        ConfigurationMap(
            mapOf(
                "NAIS_APP_NAME" to "sokos-ske-krav",
                "NAIS_NAMESPACE" to "okonomi",
                "VAULT_MOUNTPATH" to "",
                "TEAM_BEST_SLACK_WEBHOOK_URL" to "",
                "USE_TIMER" to "false",
                "TIMER_INITIAL_DELAY" to "500",
                "TIMER_INTERVAL_PERIOD" to "0",
                "SKE_REST_URL" to "",
                "MASKINPORTEN_CLIENT_ID" to "",
                "MASKINPORTEN_WELL_KNOWN_URL" to "",
                "MASKINPORTEN_CLIENT_JWK" to "",
                "MASKINPORTEN_SCOPES" to "",
                "POSTGRES_NAME" to "test",
                "POSTGRES_USERNAME" to "test",
                "POSTGRES_PASSWORD" to "test",
                // "POSTGRES_HOST" to "",
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

    val isLocal = Configuration().profile == Profile.LOCAL

    operator fun get(key: String): String = config[Key(key, stringType)]

    data class Configuration(
        val naisAppName: String = get("NAIS_APP_NAME"),
        val profile: Profile = Profile.valueOf(this["APPLICATION_PROFILE"]),
    )

    data class SftpProperties(
        val host: String = get("SFTP_SERVER"),
        val username: String = get("SKE_SFTP_USERNAME").trim(),
        val privateKeyPassword: String = get("SKE_SFTP_PASSWORD").trim(),
        val privateKey: String = get("SFTP_PRIVATE_KEY_FILE_PATH"),
        val port: Int = get("SFTP_PORT").toInt(),
    )

    data class MaskinportenClientConfig(
        val clientId: String = get("MASKINPORTEN_CLIENT_ID"),
        val authorityEndpoint: String = get("MASKINPORTEN_WELL_KNOWN_URL"),
        val rsaKey: RSAKey? = RSAKey.parse(get("MASKINPORTEN_CLIENT_JWK")),
        val scopes: String = get("MASKINPORTEN_SCOPES"),
    ) : JwtConfig(authorityEndpoint)

    @Serializable
    data class OpenIdConfiguration(
        @SerialName("jwks_uri") val jwksUri: String,
        @SerialName("issuer") val issuer: String,
        @SerialName("token_endpoint") val tokenEndpoint: String,
    )

    data object SKEConfig {
        val skeRestUrl: String = get("SKE_REST_URL")
    }

    data object PostgresConfig {
        val host: String = get("POSTGRES_HOST")
        val port: String = get("POSTGRES_PORT")
        val name: String = get("POSTGRES_NAME")
        val username: String = get("POSTGRES_USERNAME").trim()
        val password: String = get("POSTGRES_PASSWORD").trim()
        val vaultMountPath: String = get("VAULT_MOUNTPATH")
        val adminUser = "$name-admin"
        val user = "$name-user"
    }

    data object SlackConfig {
        val url: String = get("TEAM_BEST_SLACK_WEBHOOK_URL").trim()
    }

    data object TimerConfig {
        val useTimer: Boolean = get("USE_TIMER").toBoolean()
        val intervalPeriod: Duration = get("TIMER_INTERVAL_PERIOD_HOURS").toInt().hours
    }

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
