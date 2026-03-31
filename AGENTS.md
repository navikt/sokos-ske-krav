# AGENTS.md — sokos-ske-krav

## What this service does
Polls an SFTP server for fixed-width copybook flat files from NAV's legacy systems (OS, Arena, Infotrygd, Pesys), parses them into `KravLinje` records, validates them, persists them as `Krav` in PostgreSQL, and forwards them to Skatteetaten's REST API ([SKE oppdragsinnkreving-api](https://app.swaggerhub.com/apis/skatteetaten/oppdragsinnkreving-api/)). All this happens on a configurable timer loop inside `SkeService.handleNewKrav()`.

## Architecture — Data Flow
```
SFTP → FtpService → FileParser/FileValidator → LineValidator
     → KravRepository (status: KRAV_IKKE_SENDT)
     → OpprettKravService / EndreKravService / StoppKravService → SkeClient (Maskinporten auth)
     → StatusService (polls mottaksstatus) → DB status updates
```

## Key Files
| Path | Role |
|---|---|
| `src/main/kotlin/.../Application.kt` | Entry point; registers scheduled jobs via coroutine loop |
| `src/main/kotlin/.../service/SkeService.kt` | Main orchestrator — calls FTP, validate, send, resend |
| `src/main/kotlin/.../copybook/FileParser.kt` | Fixed-width column parser (byte offsets per field) |
| `src/main/kotlin/.../domain/StonadsType.kt` | Maps `(avsender, kravKode)` pairs to ytelsestype enum |
| `src/main/kotlin/.../domain/Status.kt` | All DB status strings — check here before writing status queries |
| `src/main/kotlin/.../util/RequestResult.kt` | `defineStatus()` maps SKE HTTP responses to `Status` enum; also holds SKE error-type string constants |
| `src/main/kotlin/.../config/PropertiesConfig.kt` | HOCON-based config; profile selected via `NAIS_CLUSTER_NAME` env var |
| `src/main/kotlin/.../config/CircuitBreakerManager.kt` | Resilience4j circuit breaker wrapping all SKE API calls |
| `src/main/kotlin/.../repository/KravRepository.kt` | Extension functions on `Connection` (Kotliquery pattern) |
| `src/main/kotlin/.../config/SecurityConfig.kt` | Azure AD JWT (`AUTHENTICATION_NAME`) and Basic Auth (`BASIC_AUTH_NAME`) |
| `src/main/resources/db/migration/` | Flyway migrations — naming: `V{major}.{minor}.{patch}__description.sql` |

## Authentication — Two Mechanisms
- **Outbound to SKE**: Maskinporten JWT (`MaskinportenAccessTokenProvider`) — cached, mutex-guarded token refresh.
- **Inbound `/api/*`**: Azure AD JWT (`azureAd` auth provider).
- **Inbound `/rapporter/*`**: Basic Auth (`basicAuth` provider).

## Developer Workflows

### Local Setup (required before running)
```bash
chmod 755 setupLocalEnvironment.sh && ./setupLocalEnvironment.sh
```
Requires: naisdevice running, `vault` CLI, `jq`. Generates `defaults.properties` from Vault secrets.

### Build & Run
```bash
./gradlew build       # runs ktlintFormat first (via dependsOn), then compiles + tests
./gradlew test        # Kotest with JUnit5; generates Kover HTML coverage report
./gradlew run         # starts Netty on :8080
```
- `ktlintFormat` is auto-triggered before every `KotlinCompile` task — do not skip it.
- DB migrations run automatically at startup (except locally — skipped when `isLocal = true`).

### Running Tests in IDE
Install the [Kotest](https://plugins.jetbrains.com/plugin/14080-kotest) IntelliJ plugin to run individual specs.

## Code Patterns

### Repository Layer (Kotliquery)
All DB operations are extension functions on `java.sql.Connection`. Always call via `DBUtils.asyncTransaction` (suspend) or `DBUtils.transaction` (blocking):
```kotlin
dataSource.connection.useAndHandleErrors { con -> con.getAllUnsentKrav() }
dataSource.asyncTransaction { tx -> tx.insertAllNewKrav(kravLinjer) }
```

### Status Transitions
Newly parsed krav start as `KRAV_IKKE_SENDT`. After sending: `KRAV_SENDT` → `MOTTATT_UNDERBEHANDLING` → `RESKONTROFOERT`. Error statuses map directly to HTTP codes (e.g. `HTTP503_UTILGJENGELIG_TJENESTE`). Resend queues are selected by status in `KravRepository.getAllKravForResending()`.

### Sensitive Logging
Use `TEAM_LOGS_MARKER` for any log entry that may contain PII/sensitive data:
```kotlin
logger.error(marker = TEAM_LOGS_MARKER) { "Feil: ${sensitiveData}" }
```
Regular logs go to Grafana Loki; `TEAM_LOGS_MARKER` routes to Team Logs only.

### Frontend / Rapport Code
Internal HTML report pages are marked with `@RequiresOptIn annotation class Frontend`. Apply `@OptIn(Frontend::class)` wherever these APIs are called. Do not expose these endpoints publicly.

## Language Notes

- **HOVEDSTOL** is a Norwegian word (meaning "principal/capital amount"). Never correct it to "HOOFDSTOL" or any other spelling.

- **Unit tests**: `src/test/.../service/unit/` — use MockK (`mockk`, `spyk`, `coVerify`).
- **Integration tests**: `src/test/.../service/integration/` — use `DBListener` (PostgreSQL Testcontainer) and `SftpListener` (MockFtpServer).
- **DB tests**: `src/test/.../database/` — use `DBListener.loadInitScript("SQLscript/...")` to seed data from SQL scripts in `src/test/resources/SQLscript/`.
- Circuit breaker must be reset between tests: `beforeEach { CircuitBreakerManager.circuitBreaker.reset() }`.

## Environment Config
Config is layered: `application-{env}.conf` overrides `application.conf`, merged with env vars.
`env` is determined by `NAIS_CLUSTER_NAME` (e.g. `dev-fss` → profile `dev`). Locally, `application-local.conf` is used, which reads from `defaults.properties`.

## Deployment
- NAIS FSS platform, namespace `okonomi`.
- CI/CD via GitHub Actions on merge to `main` → deploys to `dev-fss` then `prod-fss`.
- Direct push to `main` is blocked; PRs required.
- Manual deploy to test: trigger GitHub Actions on a PR branch.

