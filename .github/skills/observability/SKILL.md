---
name: observability
description: "OpenTelemetry-tracing via TraceUtils.withTracerId, MDC-propagering, x-correlation-id og TEAM_LOGS_MARKER-konvensjoner for sensitive data"
---

# Observability — tracing, MDC, logging

All async work in the service runs inside a `TraceUtils.withTracerId { }` block so that logs and metrics can be correlated end-to-end across an SFTP-batch run.

## `TraceUtils.withTracerId`

```kotlin
object TraceUtils {
    suspend fun <T> withTracerId(
        tracer: Tracer = openTelemetry.getTracer(this::class.java.canonicalName),
        spanName: String = "withTracerId",
        forceNewTrace: Boolean = false,
        block: suspend () -> T,
    ): T {
        val span = tracer.spanBuilder(spanName)
            .apply { if (forceNewTrace) setNoParent() }
            .startSpan()
        return Context.current().with(span).makeCurrent().use {
            try {
                MDC.put(LoggingContextConstants.TRACE_ID, span.spanContext.traceId)
                MDC.put(LoggingContextConstants.SPAN_ID, span.spanContext.spanId)
                val r = block(); span.setStatus(StatusCode.OK); r
            } catch (e: Exception) {
                span.setStatus(StatusCode.ERROR, e.message ?: "Unknown error")
                span.recordException(e); throw e
            } finally {
                MDC.remove(LoggingContextConstants.TRACE_ID)
                MDC.remove(LoggingContextConstants.SPAN_ID)
                span.end()
            }
        }
    }
}
```

### Usage rules

- Wrap each scheduler tick in a **new root span**: `TraceUtils.withTracerId(spanName = "handleNewKrav", forceNewTrace = true) { ... }`.
- Wrap each file's processing in a **child span**: `TraceUtils.withTracerId(spanName = "fil:$filnavn") { ... }`.
- `withTracerId` is `suspend`-friendly and sets MDC on the current coroutine context — log statements inside the block will get `trace_id` / `span_id` automatically.
- Never call `MDC.put` directly for tracing — always go through `withTracerId`.

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
- Wrap scheduler entry points in `TraceUtils.withTracerId(forceNewTrace = true)`
- Wrap per-file / per-krav work in a nested `withTracerId` with a meaningful `spanName`
- Use `TEAM_LOGS_MARKER` for any log containing PII, tokens, or raw request/response bodies
- Let `SkeClient` set `x-correlation-id` — never add it manually

### 🚫 Never
- Call `MDC.put(TRACE_ID / SPAN_ID)` outside `TraceUtils`
- Log `access_token`, private keys, or raw JWT assertions at any level
- Log PII without `TEAM_LOGS_MARKER`
- Reuse a `corrId` across unrelated krav
