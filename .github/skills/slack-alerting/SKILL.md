---
name: slack-alerting
description: "SlackService feilakkumulering og Tags-basert varsling for prosesseringsfeil i SKE-batch"
---

# Slack alerting

`SlackService` accumulates errors for a file/batch, then flushes them in a single call. Never call `SlackClient.sendMessage()` directly — accumulate first, send once.

## Accumulate → send pattern

```kotlin
slackService.addError(fileName, "Feil i validering av fil", errorMessages)
// ... more processing, more addError() calls ...
slackService.sendErrors()   // flush once at the end, safe even if no errors accumulated
```

This prevents Slack rate-limiting and groups all errors for a file into one message.

## SlackService API

```kotlin
class SlackService(private val slackClient: SlackClient = SlackClient()) {
    fun addError(fileName: String, header: String, messages: Map<String, List<String>>)
    fun addError(fileName: String, header: String, messages: Pair<String, String>)
    fun addError(fileName: String, header: String, messages: List<Pair<String, String>>)
    suspend fun sendErrors()
}
```

Internal state: `MutableList<FileErrors>`. `sendErrors()` iterates file errors, calls `SlackClient.sendMessage()` per header group, then clears the buffer.

## Tags — per-error on-call tagging

The `Tags` enum maps specific SKE error types to on-call Slack user IDs and (optionally) a routine link:

```kotlin
private enum class Tags(
    val personer: List<String>,
    val rutineLink: String? = null,
) {
    PERSON_EKSISTERER_IKKE(listOf(NAME_A, NAME_B)),
    ORGANISASJON_ER_OPPHOERT(listOf(NAME_C, NAME_D), "https://confluence.adeo.no/…"),
    // ...
    ;
    companion object { val lookupMap: Map<String, Tags> = entries.associateBy { it.name } }
}
```

The error `type` field from SKE must match the enum `name` exactly (`ORGANISASJON_ER_OPPHOERT`, etc.). `lookupMap` resolves it during `sendMessage()` so the right people are tagged.

**Adding a new tag:**
1. Add an entry to `Tags` with `personer` (and optional `rutineLink`)
2. Ensure the `name` matches the SKE error `type` exactly
3. No other changes needed — `lookupMap` and `sendMessage()` pick it up

## SlackClient

```kotlin
class SlackClient(
    private val slackEndpoint: String = PropertiesConfig.slackConfig.url,
    private val client: HttpClient = slackHttpClient,   // proxy-aware, needed on NAIS FSS
) {
    suspend fun sendMessage(
        header: String,
        fileName: String,
        messages: Map<String, List<String>>,
        taggedPeople: List<String> = emptyList(),
        rutineLink: String? = null,
    )
}
```

Webhook URL comes from `slack.url` HOCON (env `SOKOS_SKE_KRAV_SLACK_WEBHOOK_URL`).

## HOCON

```hocon
slack { url = ${SOKOS_SKE_KRAV_SLACK_WEBHOOK_URL} }
```

## Tests

```kotlin
// Option 1: real SlackService with a no-op HTTP client
val slackService = SlackService(SlackClient(slackEndpoint = "", client = MockHttpClient.slackClient))

// Option 2: mock SlackService entirely
val slackServiceMock = mockk<SlackService> {
    justRun { addError(any(), any(), any<List<Pair<String, String>>>()) }
    coJustRun { sendErrors() }
}
```

`MockHttpClient.slackClient` is a pre-built `HttpClient(MockEngine)` that returns `200 OK` to any request.

## Boundaries

### ✅ Always
- Use `addError(...)` + `sendErrors()` — never `SlackClient.sendMessage()` directly from service code
- Call `sendErrors()` exactly once at the end of each processing pass (e.g. per scheduler tick)
- Keep `Tags.name` identical to the SKE error `type` string

### ⚠️ Ask first
- Removing / renaming existing `Tags` entries (on-call people depend on them)

### 🚫 Never
- Send Slack messages per individual error (rate limiting)
- Hardcode webhook URLs or user IDs outside `Tags` / config
- Log PII inside the Slack message body without checking it's safe for the channel
