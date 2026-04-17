---
applyTo: "**/config/**/*.kt,**/api/**/*.kt,**/security/**/*.kt,**/metrics/**/*.kt,**/frontend/**/*.kt,**/dto/**/*.kt,**/domain/**/*.kt,**/util/**/*.kt"
---

# Kotlin/Ktor general patterns

Batch service (not Rapids & Rivers, not Spring Boot). For database access see `repository` skill; bootstrap/services see `service.instructions.md`; HTTP/circuit breaker see `http-client.instructions.md`; tests see `testing.instructions.md` (and `kotest` skill).

## Configuration

All config access via `PropertiesConfig` singleton (HOCON-backed). See `kotlin-app-config` skill for full layering and `@Serializable` data-class pattern.

```kotlin
val host = PropertiesConfig.sftpProperties.host
```

## Ktor routing

Health/metrics endpoints are unauthenticated; domain routes sit behind `authenticate("basic")`. Expose: `/internal/isAlive`, `/internal/isReady`, `/internal/metrics`.

## Logging

- Regular messages → Logback → Grafana Loki.
- Sensitive data (PII, saksnummer, request/response bodies, tokens) → **must** use `TEAM_LOGS_MARKER`:

```kotlin
logger.error(marker = TEAM_LOGS_MARKER) { "Feil for saksnummer: $saksnummer" }
```

## Metrics

Use the `Metrics` object (Micrometer `PrometheusMeterRegistry`) with namespace `sokos_ske_krav_*`. Register counters via `counter("<name>", "<description>")`.

## Boundaries

### ✅ Always
- `PropertiesConfig.*` for config — never `System.getenv()` in business logic
- `TEAM_LOGS_MARKER` for sensitive data

### 🚫 Never
- Commit `defaults.properties`
- Log PII/request bodies without `TEAM_LOGS_MARKER`
- Use `!!` without a preceding null check
- Call `PropertiesConfig.load()` more than once
