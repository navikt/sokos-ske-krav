---
name: kotlin-app-config
description: HOCON-basert konfigurasjon med PropertiesConfig singleton for Ktor batch-tjenester på NAIS (FSS)
---

# Kotlin Application Configuration Skill

This skill describes the HOCON + `PropertiesConfig` singleton pattern used in this project.
Config is loaded from layered HOCON files via Ktor's `ApplicationConfig` API.

## Layered HOCON Pattern

Config files are layered at startup via `mergeWithEnv()`:
1. `application.conf` – base defaults, references `defaults.properties`
2. `application-{local|dev|prod}.conf` – environment overrides
3. `defaults.properties` – local secrets (**never commit this file**)

Environment is detected via `NAIS_CLUSTER_NAME`:

```kotlin
fun ApplicationConfig.mergeWithEnv(): ApplicationConfig {
    val hoconConfig = HoconApplicationConfig(ConfigFactory.load())
    val environment =
        (System.getenv("NAIS_CLUSTER_NAME") ?: System.getProperty("NAIS_CLUSTER_NAME"))
            ?.lowercase()
            ?.substringBefore("-")
            ?: propertyOrNull("ktor.environment")?.getString()
            ?: "local"
    val environmentConfig = ApplicationConfig("application-$environment.conf")
    return environmentConfig overriding this overriding hoconConfig
}

infix fun ApplicationConfig.overriding(other: ApplicationConfig): ApplicationConfig =
    this.withFallback(other)
```

## PropertiesConfig Singleton

`PropertiesConfig` is a Kotlin `object` that holds all typed config sections as lazy properties.
Call `PropertiesConfig.load(config)` once at startup; never re-initialize.

```kotlin
object PropertiesConfig {
    lateinit var config: ApplicationConfig
        private set

    val isLocal: Boolean
        get() = applicationProperties.isLocal

    val applicationProperties by lazy {
        config.property("application").getAs<ApplicationProperties>()
    }

    val postgresConfig by lazy {
        config.property("postgres").getAs<PostgresConfig>()
    }

    val sftpProperties by lazy {
        config.property("sftp").getAs<SftpProperties>()
    }

    val maskinportenClientProperties by lazy {
        config.property("maskinportenClient").getAs<MaskinportenClientConfig>()
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
```

## Typed Config Section Data Classes

Each config section is a `@Serializable data class` deserialized via Ktor's `getAs<T>()` extension.

```kotlin
enum class Profile { LOCAL, DEV, TEST, PROD }

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
data class PostgresConfig(
    val host: String,
    val port: String,
    val name: String,
    val username: String = "",
    val password: String = "",
    val vaultMountPath: String,
) {
    val adminUser = "$name-admin"
    val user = "$name-user"
}

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
data class TimerConfig(
    val useTimer: Boolean,
    val schedulerIntervalPeriodInt: Int,
) {
    val schedulerIntervalPeriod: Duration = schedulerIntervalPeriodInt.hours
}

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
```

## Example HOCON Files

**`application.conf`** (base):
```hocon
include file("defaults.properties")

ktor {
  environment = local
}

application {
  appName = "sokos-ske-krav"
  appName = ${?NAIS_APP_NAME}
  namespace = "okonomi"
  useAuthentication = true
  basicUsername = ${?BASIC_AUTH_USERNAME}
  basicPassword = ${?BASIC_AUTH_PASSWORD}
}

postgres {
  name = "sokos-ske-krav"
  username = ${?POSTGRES_USERNAME}
  password = ${?POSTGRES_PASSWORD}
}

timer {
  useTimer = true
  schedulerIntervalPeriodInt = 4
}
```

**`application-local.conf`** (local overrides):
```hocon
include file("application.conf")

application {
  profile = LOCAL
  useAuthentication = false
}

sftp {
  host = "localhost"
  port = 22
  privateKeyFilePath = "privKey"
}
```

## Usage at Startup

```kotlin
private fun Application.module() {
    PropertiesConfig.load(environment.config.mergeWithEnv())

    val useAuthentication = PropertiesConfig.applicationProperties.useAuthentication

    if (!PropertiesConfig.isLocal) {
        PostgresDataSource.migrate()
    }

    if (!timerConfig.useTimer) return

    launchJob(skeService::handleNewKrav, timerConfig.schedulerIntervalPeriod)
}
```

## In Tests

Load a fixed test config directly from `application-test.conf`:

```kotlin
object DBListener : TestListener {
    init {
        PropertiesConfig.load(ApplicationConfig("application-test.conf"))
    }
    // ...
}
```

Mock `PropertiesConfig` with MockK when needed:

```kotlin
beforeSpec {
    mockkObject(PropertiesConfig)
    every { PropertiesConfig.config } returns ApplicationConfig("application-test.conf")
}
afterSpec {
    unmockkObject(PropertiesConfig)
}
```

## Benefits

- **Single source of truth**: All config in one singleton, no passing config objects around
- **Lazy initialization**: Config sections only parsed when first accessed
- **Type safety**: `@Serializable data class` properties with compile-time field names
- **Environment layering**: Local overrides without touching base config
- **NAIS-native**: Aligns with the HOCON layering convention used across NAV FSS services
