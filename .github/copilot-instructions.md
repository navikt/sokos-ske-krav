# Copilot Instructions – sokos-ske-krav

## What this service does

Kotlin/Ktor batch service that reads fixed-width flat files from an SFTP server, validates and parses them into tilbakekrevingskrav (debt recovery claims), persists them to PostgreSQL, and forwards them to Skatteetaten's REST API (`oppdragsinnkreving-api`). It runs on NAV's NAIS platform (FSS).

## Build, test, and lint

```bash
./gradlew build          # compile + ktlintFormat + copy pre-commit hook
./gradlew test           # all tests (requires Docker for TestContainers)
./gradlew ktlintCheck    # lint only
./gradlew ktlintFormat   # auto-fix lint violations
```

Run a single test class:
```bash
./gradlew test --tests "no.nav.sokos.ske.krav.service.unit.OpprettKravServiceTest"
```

Run a single test method (Kotest uses `--tests` with the spec class name, then filter by tag or use the [Kotest IntelliJ plugin](https://plugins.jetbrains.com/plugin/14080-kotest)):
```bash
./gradlew test --tests "no.nav.sokos.ske.krav.service.integration.OpprettKravServiceIntegrationTest"
```

The pre-commit hook runs `ktlintCheck`/`ktlintFormat` automatically. It also blocks committing `defaults.properties`.

## Architecture

### Processing pipeline

```
SFTP /inbound → FtpService.getValidatedFiles()
  → FileValidator (checksum, line count, encoding)
  → LineValidator (per-line business rules)
  → DatabaseService.saveAllNewKrav()
  → SFTP move to /outbound (or /inbound/feilfiler on error)
  → sendKrav():
      OpprettKravService  (kravtype = NYTT_KRAV)
      EndreKravService    (kravtype = ENDRING_RENTE | ENDRING_HOVEDSTOL)
      StoppKravService    (kravtype = STOPP_KRAV)
    → SkeClient → SKE REST API (Maskinporten-authenticated)
  → DatabaseService.updateSentKrav()
```

The scheduler in `Application.kt` runs `SkeService.handleNewKrav()` on a configurable interval, with a separate 24-hour job for alerting on stale krav.

### Key classes

| Class | Responsibility |
|---|---|
| `SkeService` | Orchestrates the full processing loop; owns `haltRun` flag |
| `FtpService` | SFTP download, file listing, file moves |
| `FileParser` | Fixed-width copybook parser (positional substring extraction) |
| `FileValidator` | File-level validation: count, sum, date consistency, fagsystemId |
| `LineValidator` | Per-line business rules; invalid lines are skipped and stored |
| `DatabaseService` | Facade over all repositories |
| `KravRepository` | SQL queries for the `krav` table (extension functions on `Connection`) |
| `SkeClient` | HTTP calls to SKE API |
| `StatusService` | Polls SKE for `mottaksStatus` of sent krav |
| `CircuitBreakerManager` | Resilience4j circuit breaker wrapping SKE calls; breaks the batch loop on open state |
| `StonadsType` | Enum mapping `(fagsystemId, kravKode)` pairs to kravkode + hjemmelkode |
| `PropertiesConfig` | Singleton config loader from HOCON `.conf` files |

### Database

Three tables managed by Flyway migrations in `src/main/resources/db/migration/`:
- `krav` – one row per claim line; tracks `status`, `kravtype`, `corr_id`, and `kravidentifikator_ske`
- `feilmelding` – HTTP error responses from SKE, linked to a `krav`
- `filvalideringsfeil` – file-level and line-level validation failures (not linked to a krav)

## Key conventions

### Configuration

Config is loaded from layered HOCON files. Environment is detected via `NAIS_CLUSTER_NAME`:
- `application.conf` (base)
- `application-{local|dev|prod}.conf` (environment overrides)
- `defaults.properties` (local secrets — **never commit this file**)

`PropertiesConfig` is a singleton; call `PropertiesConfig.load(config)` once at startup. Locally, run `./setupLocalEnvironment.sh` (requires `vault` and `jq`) to populate `defaults.properties`.

### Repository pattern

Repositories are Kotlin `object`s with extension functions on `Connection` or `TransactionalSession` (kotliquery). All DB access goes through `DBUtils.asyncTransaction {}` (suspending) or `DBUtils.transaction {}` (blocking). ResultSet mapping is centralised in `RepositoryMappers.kt` via the `getColumn<T>()` extension.

```kotlin
// Typical usage
dataSource.asyncTransaction { session ->
    KravRepository.run { session.insertKrav(kravLinje, fileName) }
}
```

### Status lifecycle

`Status` enum tracks every krav state. Terminal success states: `KRAV_SENDT` → `MOTTATT_UNDERBEHANDLING` → `RESKONTROFOERT` / `MIGRERT`. Retryable error states (`KRAV_IKKE_SENDT`, `HTTP409_KRAV_ER_IKKE_RESKONTROFORT_RESEND`, `HTTP500_*`, `HTTP503_*`) are picked up in the next `resendKrav()` cycle. `defineStatus()` in `RequestResult.kt` maps HTTP error responses to the correct `Status`.

### Circuit breaker

`CircuitBreakerManager.circuitBreaker` wraps every SKE HTTP call. When it opens, `sendAllOpprettKrav` / `sendAllEndreKrav` / `sendAllStoppKrav` break out of their loop and stop sending further krav in that batch. The breaker is reset each test run via `CircuitBreakerManager.circuitBreaker.reset()`.

### haltRun flag

If a file contains ≥ 1000 krav lines, `SkeService.haltRun` is set to `true` and the next scheduler invocation is skipped. It resets automatically after the large run completes.

### Fixed-width file format

`FileParser` extracts fields by byte position (no delimiter). The format is:
- Line 1: `KontrollLinjeHeader` (sender, date)
- Lines 2..N-1: `KravLinje` (each 200+ chars wide)
- Last line: `KontrollLinjeFooter` (count, checksum)

Files are processed in alphabetical order. Failed files are moved to `/inbound/feilfiler`.

### Tests

- Framework: **Kotest** `BehaviorSpec` style throughout.
- Integration tests use `DBListener` (TestContainers PostgreSQL 16) and `SftpListener`.
- Load SQL fixtures with `DBListener.loadInitScript("SQLscript/krav/ToNyeKrav.sql")`.
- Reset state with `DBListener.clearDB()` (TRUNCATE + RESTART IDENTITY).
- Mock HTTP with `MockHttpClient.client(MockResponse(endpoint, body, status))`.
- Unit tests mock `SkeClient` and `DatabaseService` via MockK.

### Logging

Sensitive data must use the `TEAM_LOGS_MARKER` (Logback marker) so it routes to Team Logs instead of Grafana Loki. Regular info/error logs go to Loki.
