# Typed Config Classes & Testing

## Typed Config Section Data Classes

Each config section is a `@Serializable data class` deserialized via Ktor's `getAs<T>()` extension.

```kotlin
enum class Profile { LOCAL, DEV, TEST, PROD }

@Serializable
data class ApplicationProperties(
    val profile: Profile,
    val appName: String,
    val namespace: String,
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
