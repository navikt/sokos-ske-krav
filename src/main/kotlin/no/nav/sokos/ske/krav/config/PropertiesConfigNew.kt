package no.nav.sokos.ske.krav.config

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlinx.serialization.Serializable

import com.nimbusds.jose.jwk.RSAKey
import com.typesafe.config.ConfigFactory
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.HoconApplicationConfig
import io.ktor.server.config.getAs
import io.ktor.server.config.withFallback

object PropertiesConfigNew {
    lateinit var config: ApplicationConfig
        private set

    val isLocal: Boolean
        get() = applicationProperties.isLocal

    val useTimer: Boolean
        get() = timerConfig.useTimer

    val applicationProperties by lazy {
        config.property("application").getAs<ApplicationProperties>()
    }

    val azureAdProperties by lazy {
        config.property("azureAd").getAs<AzureAdProperties>()
    }

    val sftpProperties by lazy {
        config.property("sftp").getAs<SftpProperties>()
    }

    val maskinportenClientProperties by lazy {
        config.property("maskinportenClient").getAs<MaskinportenClientConfig>()
    }

    val skeRestConfig by lazy {
        config.property("skeConfig").getAs<SkeConfig>()
    }

    val postgresConfig by lazy {
        config.property("postgres").getAs<PostgresConfig>()
    }

    val slackConfig by lazy {
        config.property("slackConfig").getAs<SlackConfig>()
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

fun ApplicationConfig.mergeWithEnv(): ApplicationConfig {
    val hoconConfig = HoconApplicationConfig(ConfigFactory.load())
    val environment =
        (System.getenv("NAIS_CLUSTER_NAME") ?: System.getProperty("NAIS_CLUSTER_NAME"))
            ?.lowercase()
            ?.substringBefore("-")
            ?: propertyOrNull("ktor.environment")?.getString()
            ?: "local"
    val environmentConfig = ApplicationConfig("application-$environment.conf")
    return this overriding environmentConfig overriding hoconConfig
}

infix fun ApplicationConfig.overriding(other: ApplicationConfig): ApplicationConfig = this.withFallback(other)

enum class Profile {
    LOCAL,
    DEV,
    TEST,
    PROD,
}

@Serializable
data class ApplicationProperties(
    val profile: Profile,
    val appName: String,
    val useAuthentication: Boolean,
    val basicUsername: String,
    val basicPassword: String,
) {
    val isLocal = profile == Profile.LOCAL
}

@Serializable
data class AzureAdProperties(
    val clientId: String,
    val wellKnownUrl: String,
    val tenantId: String,
    val clientSecret: String,
)

@Serializable
data class SftpProperties(
    val host: String,
    val username: String,
    val privateKeyPassword: String,
    val privateKey: String,
    val port: Int,
)

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
    val username: String,
    val password: String,
    val vaultMountPath: String,
) {
    val adminUser = "$name-admin"
    val user = "$name-user"
}

@Serializable
data class SlackConfig(
    val url: String,
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
