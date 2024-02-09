package sokos.ske.krav.config

import com.natpryce.konfig.*
import com.nimbusds.jose.jwk.RSAKey
import io.ktor.client.call.*
import io.ktor.client.request.get
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import sokos.ske.krav.util.httpClient
import java.io.File

object PropertiesConfig {

	private val defaultProperties = ConfigurationMap(
		mapOf(
			"NAIS_APP_NAME" to "sokos-ske-krav",
			"NAIS_NAMESPACE" to "okonomi",
		)
	)
	private val localDevProperties = ConfigurationMap(
		"APPLICATION_PROFILE" to Profile.LOCAL.toString(),
		"SFTP_HOST_KEY_FILE_PATH" to "hostKey",
		"SFTP_PRIVATE_KEY_FILE_PATH" to "privKey",
		"FTP_SERVER" to "10.183.32.98",
		"FTP_PORT" to "22",
		"FTP_DIRECTORY" to "/",
		"SKE_REST_URL" to "",

		"POSTGRES_HOST" to "dev-pg.intern.nav.no",
		"POSTGRES_PORT" to "5432",
		"POSTGRES_NAME" to "sokos-skd-krav",

		"VAULT_MOUNTPATH" to "",
	)

	private val devProperties = ConfigurationMap(mapOf("APPLICATION_PROFILE" to Profile.DEV.toString()))
	private val prodProperties = ConfigurationMap(mapOf("APPLICATION_PROFILE" to Profile.PROD.toString()))

	private val config = when (System.getenv("NAIS_CLUSTER_NAME") ?: System.getProperty("NAIS_CLUSTER_NAME")) {
		"dev-fss" -> ConfigurationProperties.systemProperties() overriding EnvironmentVariables() overriding devProperties overriding defaultProperties
		"prod-fss" -> ConfigurationProperties.systemProperties() overriding EnvironmentVariables() overriding prodProperties overriding defaultProperties
		else ->
			ConfigurationProperties.systemProperties() overriding EnvironmentVariables() overriding ConfigurationProperties.fromOptionalFile(
				File("defaults.properties")
			) overriding localDevProperties overriding defaultProperties
	}

	enum class Profile {
		LOCAL, DEV, PROD
	}

	operator fun get(key: String): String = config[Key(key, stringType)]
	data class Configuration(
		val naisAppName: String = get("NAIS_APP_NAME"),
		val profile: Profile = Profile.valueOf(this["APPLICATION_PROFILE"]),
	)

	data class FtpConfig(
		val server: String = get("FTP_SERVER"),
		val username: String = get("SKE_SFTP_USERNAME"),
		val keyPass: String = get("SKE_SFTP_PASSWORD"),
		val privKey: String = get("SFTP_PRIVATE_KEY_FILE_PATH"),
		val hostKey: String = get("SFTP_HOST_KEY_FILE_PATH"),
		val port: Int = get("FTP_PORT").toInt()
	)

	data class MaskinportenClientConfig(
		val clientId: String = get("MASKINPORTEN_CLIENT_ID"),
		val authorityEndpoint: String = get("MASKINPORTEN_WELL_KNOWN_URL"),
		val rsaKey: RSAKey? = RSAKey.parse(get("MASKINPORTEN_CLIENT_JWK")),
		val scopes: String = get("MASKINPORTEN_SCOPES"),
	) : JwtConfig(authorityEndpoint)

	data class MqConfig(
		val oppdragQueue: String = get("MQ_TIL_OPPDRAG"),
		val oppdragBoq: String = get("MQ_TIL_OPPDRAG_BOQ"),
		val host: String = get("MQ_HOST"),
		val port: String = get("MQ_PORT"),
		val qmgr: String = get("MQ_QUEMANAGER"),
		val channel: String = get("MQ_CHANNEL"),
		val username: String = get("MQ_USERNAME"),
		val password: String = get("MQ_PASSWORD")
	)

	@Serializable
	data class OpenIdConfiguration(
		@SerialName("jwks_uri") val jwksUri: String,
		@SerialName("issuer") val issuer: String,
		@SerialName("token_endpoint") val tokenEndpoint: String,
	)

	data class SKEConfig(
		val skeRestUrl: String = get("SKE_REST_URL")
	)

	data class PostgresConfig(
		val host: String = get("POSTGRES_HOST"),
		val port: String = get("POSTGRES_PORT"),
		val name: String = get("POSTGRES_NAME"),
		val username: String = if (Profile.valueOf(this["APPLICATION_PROFILE"]) == Profile.LOCAL) get("POSTGRES_USERNAME").trim() else "",
		val password: String = if (Profile.valueOf(this["APPLICATION_PROFILE"]) == Profile.LOCAL) get("POSTGRES_PASSWORD").trim() else "",
		val vaultMountPath: String = get("VAULT_MOUNTPATH"),
	) {
		val jdbcUrl: String = "jdbc:postgresql://$host:$port/$name"
	}

	open class JwtConfig(private val wellKnownUrl: String) {
		val openIdConfiguration: OpenIdConfiguration by lazy {
			runBlocking {
				httpClient.get(wellKnownUrl).body()
			}
		}
	}
}
