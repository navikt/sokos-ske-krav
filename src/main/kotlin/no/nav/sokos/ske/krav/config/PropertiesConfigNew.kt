package no.nav.sokos.ske.krav.config

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

    val applicationProperties by lazy {
        config.property("application").getAs<ApplicationProperties>()
    }

    val azureAdProperties by lazy {
        config.property("azureAd").getAs<AzureAdProperties>()
    }

    val maskinportenClientProperties by lazy {
        config.property("maskinportenClient").getAs<MaskinportenClientConfig>()
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
