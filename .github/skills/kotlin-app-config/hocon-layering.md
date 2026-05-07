# HOCON Layering & Example Config Files

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
  privateKeyFilePath = "privateKey"
}
```
