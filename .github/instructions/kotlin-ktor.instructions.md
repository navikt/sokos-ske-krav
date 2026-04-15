---
applyTo: "**/*.kt"
---

Ktor batch-service mønstre for NAV FSS-backends: PropertiesConfig, object repository med Connection-extensions, DBUtils, Resilience4j circuit breaker og Kotest BehaviorSpec.

> Apply these patterns when working in `sokos-ske-krav`. This is a **batch service** (not Rapids & Rivers, not Spring Boot). It reads fixed-width flat files from SFTP, validates them, persists to PostgreSQL, and forwards to SKE REST API via Maskinporten-authenticated HTTP calls.

# Kotlin/Ktor Development Standards

## Application Bootstrap

Use `embeddedServer(Netty)` and call `PropertiesConfig.load(environment.config.mergeWithEnv())` first:

```kotlin
fun main() {
    embeddedServer(Netty, port = 8080, module = Application::module).start(true)
}

private fun Application.module() {
    PropertiesConfig.load(environment.config.mergeWithEnv())

    val applicationState = ApplicationState()
    val skeService = SkeService()

    commonConfig()
    applicationLifecycleConfig(applicationState)
    securityConfig()
    routingConfig(PropertiesConfig.applicationProperties.useAuthentication, applicationState)

    if (!PropertiesConfig.isLocal) {
        PostgresDataSource.migrate()
    }

    if (!timerConfig.useTimer) return

    launchJob(skeService::handleNewKrav, timerConfig.schedulerIntervalPeriod)
    launchJob(databaseService::deleteOldData, 24.hours)
}

private fun CoroutineScope.launchJob(
    function: suspend () -> Unit,
    delayDuration: Duration,
) = launch {
    while (true) {
        try {
            function()
            delay(delayDuration)
        } catch (_: CancellationException) {
            logger.info { "Scheduled task cancelled" }
            break
        } catch (e: Exception) {
            logger.error(e) { "Feil i scheduled task" }
        }
    }
}
```

## Configuration Pattern

Use `PropertiesConfig` singleton with HOCON layered files. See `kotlin-app-config` skill for full details.

```kotlin
// Read config sections anywhere via PropertiesConfig
val host = PropertiesConfig.sftpProperties.host
val isLocal = PropertiesConfig.isLocal
```

## Database Access

### DataSource

`PostgresDataSource` is a singleton `object` using HikariCP. In non-local environments it integrates with Vault for credentials:

```kotlin
object PostgresDataSource {
    val dataSource: HikariDataSource by lazy { dataSource() }

    fun migrate() {
        dataSource(role = postgresConfig.adminUser).use { migrate(it) }
    }

    fun migrate(dataSource: HikariDataSource) {
        Flyway.configure()
            .dataSource(dataSource)
            .initSql("""SET ROLE "${postgresConfig.adminUser}"""")
            .lockRetryCount(-1)
            .validateMigrationNaming(true)
            .load()
            .migrate()
    }
}
```

### Transactions via DBUtils

Always use `DBUtils.asyncTransaction {}` (suspending) or `DBUtils.transaction {}` (blocking). Never use bare JDBC connections directly.

```kotlin
// Suspending (use in coroutine context / service layer)
dataSource.asyncTransaction { session ->
    KravRepository.run { session.insertAllNewKrav(kravLinjer, filnavn) }
}

// Blocking (use in sync contexts)
dataSource.transaction { session ->
    KravRepository.run { session.updateStatus(corrId, status) }
}
```

### Repository Pattern

Repositories are Kotlin `object`s with extension functions on `Connection` (raw JDBC) or `TransactionalSession` (kotliquery). Use `RepositoryExtensions` helpers:

```kotlin
object KravRepository {
    fun Connection.getAllUnsentKrav() =
        executeSelect(
            """select * from krav where status = ?""",
            Status.KRAV_IKKE_SENDT.value,
        ).toKrav()

    fun Connection.updateSentKrav(
        corrId: String,
        kravidentifikatorSKE: String,
        status: String,
    ) = executeUpdate(
        """update krav set kravidentifikator_ske = ?, status = ?, tidspunkt_sendt = now() where corr_id = ?""",
        kravidentifikatorSKE,
        status,
        corrId,
    )

    // For kotliquery TransactionalSession (e.g. when returnGeneratedKey is needed)
    fun getKravTableIdFromCorrelationId(
        tx: TransactionalSession,
        corrID: String,
    ): Long =
        tx.single(
            queryOf("select id from krav where corr_id = ?", corrID)
                .map { row -> row.long("id") }
                .asSingle,
        ) ?: throw IllegalStateException("Krav med corrId $corrID ikke funnet")
}
```

### ResultSet Mapping

Centralise `ResultSet` → domain mapping in `RepositoryMappers.kt` using `getColumn<T>()`:

```kotlin
fun ResultSet.toKrav() =
    toList {
        Krav(
            kravId = getColumn("id"),
            saksnummerNAV = getColumn("saksnummer_nav"),
            status = getColumn("status"),
            kravtype = getColumn("kravtype"),
            corrId = getColumn("corr_id"),
            kravidentifikatorSKE = getColumn("kravidentifikator_ske"),
            // ... remaining fields
        )
    }

private fun <T> ResultSet.toList(mapper: ResultSet.() -> T) =
    buildList {
        while (next()) { add(mapper()) }
    }
```

### Error Handling in Repositories

Wrap bare `Connection` usage with `useAndHandleErrors`:

```kotlin
dataSource.connection.useAndHandleErrors { con ->
    con.getAllUnsentKrav()
}
```

## Service Layer Pattern

Services are regular classes with default-argument injection for testability:

```kotlin
class DatabaseService(
    private val dataSource: HikariDataSource = PostgresDataSource.dataSource,
) {
    fun getAllUnsentKrav(): List<Krav> =
        dataSource.connection.useAndHandleErrors {
            it.getAllUnsentKrav()
        }

    fun saveAllNewKrav(kravLinjer: List<KravLinje>, filnavn: String) =
        dataSource.connection.useAndHandleErrors {
            it.insertAllNewKrav(kravLinjer, filnavn)
        }
}
```

The orchestrating service (`SkeService`) owns a `haltRun` flag. Set it to `true` when a file contains ≥ 1000 krav lines; the next scheduler invocation is skipped. Reset after the large run completes.

## HTTP Client & Circuit Breaker

Configure the shared `httpClient` with the `CircuitBreakerPlugin` and proxy routing:

```kotlin
val httpClient =
    HttpClient(Apache5) {
        expectSuccess = false
        install(CircuitBreakerPlugin)
        install(ContentNegotiation) { json(jsonConfig) }
        engine {
            customizeClient {
                setRoutePlanner(SystemDefaultRoutePlanner(ProxySelector.getDefault()))
            }
        }
    }.apply {
        plugin(HttpSend).intercept { guardCall { execute(it) } }
    }
```

`CircuitBreakerManager.guardCall {}` wraps every SKE HTTP call. When the breaker opens, `sendAll*` loops break out and no further krav are sent in that batch. Always reset in tests:

```kotlin
beforeEach { CircuitBreakerManager.circuitBreaker.reset() }
```

## Ktor Routing

Health and metrics endpoints are unauthenticated; API routes require Basic auth:

```kotlin
fun Application.routingConfig(useAuthentication: Boolean, applicationState: ApplicationState) {
    routing {
        get("/internal/isAlive") {
            if (applicationState.running) call.respondText("Alive") else call.respond(HttpStatusCode.ServiceUnavailable)
        }
        get("/internal/isReady") { call.respondText("Ready") }
        get("/internal/metrics") { call.respondText(Metrics.registry.scrape()) }

        authenticate("basic") {
            // authenticated routes
        }
    }
}
```

## Logging

- Regular info/error → standard Logback (routes to Grafana Loki)
- Sensitive data (PII, request/response bodies) → **must** use `TEAM_LOGS_MARKER`:

```kotlin
private val logger = KotlinLogging.logger {}

// Regular log
logger.info { "Behandler fil: $filnavn" }

// Sensitive log (routes to Team Logs, not Loki)
logger.error(marker = TEAM_LOGS_MARKER) { "Feil med saksnummer: $saksnummer - ${exception.message}" }
```

## Metrics

Use `Metrics` object with Micrometer `PrometheusMeterRegistry`:

```kotlin
object Metrics {
    val registry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    private const val NAMESPACE = "sokos_ske_krav"

    val numberOfKravSent: Counter by lazy { counter("krav_sendt_til_ske", "Antall krav sendt") }

    private fun counter(name: String, description: String) =
        Counter.builder("${NAMESPACE}_$name")
            .description(description)
            .register(registry)
}

// Increment in service
Metrics.numberOfKravSent.increment()
```

## Testing

### Unit Tests

Use **Kotest `FunSpec`** or **`BehaviorSpec`** (never JUnit). Mock with **MockK**:

```kotlin
class OpprettKravServiceTest : FunSpec({
    val databaseServiceMock = mockk<DatabaseService> {
        justRun { updateSentKrav(any<List<RequestResult>>()) }
    }

    test("sendAllOpprettKrav skal returnere liste av innsendte krav") {
        val skeClientMock = mockk<SkeClient>()
        val service = OpprettKravService(skeClientMock, databaseServiceMock)
        // ...
    }
})
```

### Integration Tests (DB)

Use `DBListener` (Kotest `TestListener` wrapping TestContainers PostgreSQL 16). Register with `extensions(DBListener)`:

```kotlin
internal class OpprettKravServiceIntegrationTest : BehaviorSpec({
    extensions(DBListener)
    beforeEach { CircuitBreakerManager.circuitBreaker.reset() }

    val dbService = DatabaseService(DBListener.dataSource)

    Given("2 nye krav som ikke er sendt") {
        DBListener.clearDB()
        DBListener.loadInitScript("SQLscript/krav/ToNyeKrav.sql")

        val krav = dbService.getAllUnsentKrav()
        krav.size shouldBe 2

        When("SKE svarer med OK") {
            val httpClient = MockHttpClient.client(
                MockResponse(Endpoint.OPPRETT, nyttKravResponse("4321"), HttpStatusCode.OK)
            )
            val skeClient = SkeClient(
                skeEndpoint = "",
                client = httpClient,
                tokenProvider = mockk(relaxed = true)
            )
            val service = OpprettKravService(skeClient, DatabaseService(DBListener.dataSource))
            val results = service.sendAllOpprettKrav(krav)

            Then("Kravene skal ha fått kravidentifikatorSKE") {
                results.size shouldBe 2
                dbService.getAllUnsentKrav().size shouldBe 0
            }
        }
    }
})
```

### Integration Tests (SFTP)

Use `SftpListener` in the same way:

```kotlin
internal class FtpServiceIntegrationTest : BehaviorSpec({
    extensions(SftpListener)
    // ...
})
```

### Mock HTTP

Use `MockHttpClient.client(vararg MockResponse)` — matches requests by endpoint path:

```kotlin
val client = MockHttpClient.client(
    MockResponse(Endpoint.OPPRETT, nyttKravResponse("id-123"), HttpStatusCode.OK),
    MockResponse(Endpoint.MOTTAKSSTATUS, mottaksStatusResponse(), HttpStatusCode.OK),
)
```

## Boundaries

### ✅ Always

- Use `PropertiesConfig` for all config access — never `System.getenv()` directly in business logic
- Use `DBUtils.asyncTransaction {}` / `DBUtils.transaction {}` — never bare `Connection` in service code
- Use `object` + extension functions for repositories
- Map `ResultSet` → domain in `RepositoryMappers.kt` using `getColumn<T>()`
- Log sensitive data only with `TEAM_LOGS_MARKER`
- Reset `CircuitBreakerManager.circuitBreaker` in `beforeEach` of tests that make SKE calls
- Use Kotest `BehaviorSpec` (Given/When/Then) for integration tests, `FunSpec` or `BehaviorSpec` for unit tests

### ⚠️ Ask First

- Changing the scheduler interval or `haltRun` threshold
- Adding new Maskinporten scopes
- Modifying file format / copybook field positions
- Changing circuit breaker configuration

### 🚫 Never

- Commit `defaults.properties`
- Log PII/request bodies without `TEAM_LOGS_MARKER`
- Use `!!` (non-null assertion) without a preceding null check
- Skip Flyway migrations for schema changes
- Call `PropertiesConfig.load()` more than once (it is guarded but callers should not rely on that)
