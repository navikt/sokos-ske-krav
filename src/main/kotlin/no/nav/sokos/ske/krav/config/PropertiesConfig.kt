package no.nav.sokos.ske.krav.config

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlinx.serialization.Serializable

import com.nimbusds.jose.jwk.RSAKey
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.getAs

object PropertiesConfig {
    lateinit var config: ApplicationConfig
        private set

    val isLocal: Boolean
        get() = applicationProperties.isLocal

    val applicationProperties by lazy {
        config.property("application").getAs<ApplicationProperties>()
    }

    val azureProperties by lazy {
        config.property("azure").getAs<AzureProperties>()
    }

    val sftpProperties by lazy {
        config.property("sftp").getAs<SftpProperties>()
    }

    val maskinportenClientProperties by lazy {
        config.property("maskinportenClient").getAs<MaskinportenClientConfig>()
    }

    val skeRestConfig by lazy {
        config.property("ske").getAs<SkeConfig>()
    }

    val postgresConfig by lazy {
        config.property("postgres").getAs<PostgresConfig>()
    }

    val slackConfig by lazy {
        config.property("slack").getAs<SlackConfig>()
    }

    val circuitBreakerConfig by lazy {
        config.property("circuitBreaker").getAs<CircuitBreakerConfig>()
    }

    val timerConfig by lazy {
        config.property("timer").getAs<TimerConfig>()
    }

    fun load(applicationConfig: ApplicationConfig) {
        if (!::config.isInitialized) {
            config = applicationConfig
        }
    }
}

fun loadConfig(): ApplicationConfig = ApplicationConfig("application.conf")

enum class Profile {
    LOCAL,
    DEV,
    DEV_GCP,
    TEST,
    PROD,
}

@Serializable
data class ApplicationProperties(
    val profile: Profile,
    val appName: String,
    val namespace: String,
    val useAuthentication: Boolean,
    val basicUsername: String,
    val basicPassword: String,
) {
    val isLocal = profile == Profile.LOCAL
}

@Serializable
data class AzureProperties(
    val jwksUri: String,
    val configIssuer: String,
    val clientId: String,
)

@Serializable
data class SftpProperties(
    val host: String,
    val username: String,
    val privateKeyPassword: String,
    val privateKeyFilePath: String,
    val port: Int,
) {
    val trimmedUsername: String get() = username.trim()
    val trimmedPrivateKeyPassword: String get() = privateKeyPassword.trim()
}

@Serializable
data class MaskinportenClientConfig(
    val clientId: String,
    val wellKnownUrl: String,
    val rsaKeyString: String,
    val scopes: String,
) {
    val rsaKey: RSAKey? by lazy {
        RSAKey.parse(rsaKeyString)
    }
}

@Serializable
data class SkeConfig(
    val skeRestUrl: String,
)

@Serializable
data class PostgresConfig(
    val host: String,
    val port: String,
    val name: String,
    val username: String = "",
    val password: String = "",
    val url: String,
)

@Serializable
data class SlackConfig(
    val url: String,
    val slackIdProductLeader: String,
    val slackIdDomainSpecialists: String,
    val slackIdTechnicalSpecialist: String,
)

@Serializable
data class CircuitBreakerConfig(
    val waitDurationInOpenState: Long,
) {
    companion object {
        const val SLIDING_WINDOW_SIZE: Int = 1
        const val MINIMUM_NUMBER_OF_CALLS: Int = 1
        const val FAILURE_RATE_THRESHOLD: Float = 100.0f
        const val PERMITTED_NUMBER_OF_CALLS_IN_HALF_OPEN_STATE: Int = 1
    }
}

@Serializable
data class TimerConfig(
    val useTimer: Boolean,
    val schedulerIntervalPeriodInt: Int,
) {
    val schedulerIntervalPeriod: Duration = schedulerIntervalPeriodInt.hours
}
