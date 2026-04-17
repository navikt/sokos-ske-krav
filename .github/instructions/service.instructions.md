---
applyTo: "**/service/**/*.kt,**/Application.kt"
---

# Service layer & application bootstrap

Services are regular classes with default-argument injection (no DI framework). `SkeService` orchestrates processing and owns `haltRun`. `Application.kt` wires Ktor and starts scheduled jobs.

## Bootstrap order (in `Application.module()`)

1. `PropertiesConfig.load(environment.config.mergeWithEnv())` — first, exactly once
2. Construct `ApplicationState` and services
3. `commonConfig()`, `applicationLifecycleConfig()`, `securityConfig()`, `routingConfig(...)`
4. `PostgresDataSource.migrate()` when not local
5. `launchJob(skeService::handleNewKrav, timerConfig.schedulerIntervalPeriod)` and `launchJob(databaseService::deleteOldData, 24.hours)` — only if `timerConfig.useTimer`

## Scheduler

`launchJob` loops with `delay(...)`, handles `CancellationException` to break, logs & continues on other exceptions. Never replace with `GlobalScope.launch`.

## Service class pattern

```kotlin
class DatabaseService(
    private val dataSource: HikariDataSource = PostgresDataSource.dataSource,
) { /* ... */ }
```

Use default-argument injection so tests can pass `DBListener.dataSource`, `mockk(...)`, etc.

## `haltRun`

`SkeService.haltRun` is set to `true` when a file contains ≥ 1000 krav lines; the next scheduler tick is skipped. Reset after the large run completes.

## Boundaries

### ✅ Always
- Default-argument injection on services
- `TEAM_LOGS_MARKER` for sensitive data

### ⚠️ Ask first
- Changing scheduler interval or `haltRun` threshold

### 🚫 Never
- Call `PropertiesConfig.load()` more than once
- Use `GlobalScope.launch` / `runBlocking` in production code
