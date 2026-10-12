---
name: observability
description: "OpenTelemetry-tracing with @WithSpan, x-correlation-id og TEAM_LOGS_MARKER-konvensjoner for sensitive data"
---

# Observability — tracing and logging

Use OpenTelemetry's `@WithSpan` annotation to create spans for scheduled jobs and important processing operations. The instrumentation creates spans around annotated methods and preserves the active trace context across calls.

## `@WithSpan`

```kotlin
import io.opentelemetry.instrumentation.annotations.WithSpan

@WithSpan
suspend fun handleNewKrav() {
    // Process the scheduled batch
}
```

### Usage rules

- Annotate scheduler entry-point methods, such as `SkeService.handleNewKrav`, with `@WithSpan`.
- Annotate additional processing methods when a distinct child span is useful; each annotated method creates a span within the active trace context.
- Use the annotation on the method rather than wrapping calls in a custom tracing block.

## x-correlation-id (SKE API)

Every request to SKE carries `x-correlation-id: ${krav.corrId}`. `corrId` is assigned on insert (`Krav.corrId = UUID.randomUUID().toString()`) and is the same for both `ENDRING_HOVEDSTOL` and `ENDRING_RENTE` rows of the same endring. The header is appended by `SkeClient.doPost` / `doPut` — do not add it manually in service code.

## TEAM_LOGS_MARKER

`TEAM_LOGS_MARKER` (defined in `config/CommonConfig.kt`) routes log statements to a restricted-access sink suitable for PII. **Must** be used for anything that touches:

- `gjelderId` (fnr / orgnr), `saksnummerNAV`, full request/response bodies
- Maskinporten JWT assertions and token-provider errors
- SFTP session details (host, keys, JSch errors)
- Validation error messages that include raw file content

```kotlin
logger.error(marker = TEAM_LOGS_MARKER) {
    "Feil for saksnummer: $saksnummer - body=$body"
}
```

Regular `logger.info { ... }` without a marker → public Grafana Loki. Assume anyone in NAV can read it.

## Boundaries

### ✅ Always
- Annotate scheduler entry points with `@WithSpan`
- Add `@WithSpan` to per-file / per-krav methods when a distinct span is useful
- Use `TEAM_LOGS_MARKER` for any log containing PII, tokens, or raw request/response bodies
- Let `SkeClient` set `x-correlation-id` — never add it manually

### 🚫 Never
- Log `access_token`, private keys, or raw JWT assertions at any level
- Log PII without `TEAM_LOGS_MARKER`
- Reuse a `corrId` across unrelated krav
