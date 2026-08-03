---
name: kotlin-app-config
description: HOCON-basert konfigurasjon med PropertiesConfig singleton for Ktor batch-tjenester på NAIS (FSS)
---

# Kotlin Application Configuration Skill

This skill describes the HOCON + `PropertiesConfig` singleton pattern used in this project.
Config is loaded from a HOCON file via Ktor's `ApplicationConfig` API.

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

## Sub-files

- See [hocon-layering.md](hocon-layering.md) for the HOCON pattern (`mergeWithEnv()`) and example HOCON config files.
- See [config-classes.md](config-classes.md) for typed config section data classes (`@Serializable`) and testing configuration with MockK.

## Benefits

- **Single source of truth**: All config in one singleton, no passing config objects around
- **Lazy initialization**: Config sections only parsed when first accessed
- **Type safety**: `@Serializable data class` properties with compile-time field names
- **Environment layering**: Local overrides without touching base config
- **NAIS-native**: Aligns with the HOCON layering convention used across NAV FSS services
