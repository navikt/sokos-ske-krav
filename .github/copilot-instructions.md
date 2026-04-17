# Copilot Instructions – sokos-ske-krav

## What this service does

Kotlin/Ktor batch service on NAV's NAIS (FSS) platform. Reads fixed-width flat files from SFTP, validates and parses them into tilbakekrevingskrav, persists to PostgreSQL, and forwards them to Skatteetaten's `oppdragsinnkreving-api` (Maskinporten-authenticated).

## Build, test, lint

```bash
./gradlew build          # compile + ktlintFormat + copy pre-commit hook
./gradlew test           # all tests (Docker required for TestContainers)
./gradlew ktlintCheck    # lint only
./gradlew test --tests "<FQN>"   # single test
```

The pre-commit hook runs ktlint and blocks committing `defaults.properties`.

## Processing pipeline

```
SFTP /inbound → FtpService → FileValidator → LineValidator
  → DatabaseService.saveAllNewKrav → SFTP move (/outbound or /inbound/feilfiler)
  → OpprettKravService | EndreKravService | StoppKravService
  → SkeClient → SKE REST API → DatabaseService.updateSentKrav
  → StatusService.getMottaksStatus (polls /mottaksstatus on a separate schedule)
  → RapportService + /rapporter/avstemming (manual operator follow-up for stuck krav)
```

Status lifecycle: `KRAV_IKKE_SENDT → KRAV_SENDT → MOTTATT_UNDERBEHANDLING → RESKONTROFOERT/MIGRERT`. Retryable error states (`HTTP409_*_RESEND`, `HTTP500_*`, `HTTP503_*`) are re-picked by `resendKrav()`. `defineStatus()` in `RequestResult.kt` maps HTTP errors to `Status`.

## Key classes

| Class | Responsibility |
|---|---|
| `SkeService` | Orchestrates processing loop; owns `haltRun` flag |
| `FtpService`, `SftpConfig` | SFTP download, listing, moves |
| `FileParser` | Fixed-width copybook parser (positional extraction) |
| `FileValidator`, `LineValidator` | Two-phase validation (file then line) |
| `DatabaseService` | Facade over `KravRepository`, `FeilmeldingRepository`, `FilvalideringsfeilRepository` |
| `SkeClient` | Ktor HTTP client to SKE (Apache5 engine, proxy-aware, circuit-breaker-guarded) |
| `StatusService` | Polls SKE for `mottaksStatus`; progresses krav to `RESKONTROFOERT` |
| `RapportService` | Frontend-only (`@Frontend`) reconciliation of stuck krav |
| `TraceUtils` | OpenTelemetry span + MDC helper; wrap every scheduler entry point |
| `CircuitBreakerManager` | Resilience4j breaker wrapping every SKE call |
| `StonadsType` | Enum mapping `(kravKode, kodeHjemmel) → stønadstype` |
| `PropertiesConfig` | HOCON-backed singleton; load once at startup |

## Database

Three Flyway-managed tables in `src/main/resources/db/migration/`:
- `krav` – one row per claim line (`status`, `kravtype`, `corr_id`, `kravidentifikator_ske`)
- `feilmelding` – SKE HTTP error responses, FK to `krav`
- `filvalideringsfeil` – file/line validation failures (no FK to krav)

## Scoped instructions (auto-loaded by `applyTo`)

| File | Applies to | Summary |
|---|---|---|
| `kotlin-ktor.instructions.md` | `config`, `api`, `security`, `metrics`, `frontend`, `dto`, `domain`, `util` | Routing, logging marker, metrics |
| `service.instructions.md` | `service`, `Application.kt` | Bootstrap, scheduler, `haltRun`, service-class injection |
| `http-client.instructions.md` | `client`, `config/Circuit*.kt`, `config/HttpClient*.kt` | HTTP client config, circuit breaker |
| `testing.instructions.md` | `**/test/**/*.kt` | Kotest/MockK essentials; see `kotest` skill for details |

Lazy-loaded skills are listed automatically by the runtime — invoke by name when working on the matching area.

## Global rules

- **Config**: access via `PropertiesConfig.*`; never `System.getenv()` directly in business logic. Call `PropertiesConfig.load()` exactly once (in `Application.module()`).
- **Sensitive logging**: PII, request/response bodies, tokens, private keys → always `TEAM_LOGS_MARKER`. Never log raw `access_token`.
- **Null safety**: no `!!` without a preceding null check.
- **`defaults.properties`**: never commit.
- **SKE calls**: always go through `CircuitBreakerManager.guardCall {}`.
