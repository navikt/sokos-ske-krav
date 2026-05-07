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
    val errorKey: String? = null,
) {
    PERSON_EKSISTERER_IKKE(listOf(NAME_A, NAME_B)),
    ORGANISASJON_ER_OPPHOERT(listOf(NAME_C, NAME_D), "https://confluence.adeo.no/…"),
    FANT_IKKE_GYLDIG_KRAVIDENTIFIKATOR(listOf(NAME_E), errorKey = FeilResponse.CustomTitles.FANT_IKKE_GYLDIG_KRAVIDENTIFIKATOR),
    // ...
    ;
    companion object { val lookupMap: Map<String, Tags> = entries.associateBy { it.errorKey ?: it.name } }
}
```

The error key in the `messages` map passed to `addError` must match either `it.errorKey` (if set) or `it.name` exactly. `SlackService.sendErrors()` resolves it through `lookupMap` before calling `SlackClient.sendMessage(...)`, so the right people are tagged.

- For **async SKE validation errors**: the key is `valideringsFeil.error` which comes directly from SKE in SCREAMING_SNAKE_CASE — use a matching enum `name`.
- For **internally detected errors** (e.g. `FeilResponse.CustomTitles.FANT_IKKE_GYLDIG_KRAVIDENTIFIKATOR` set as `feilResponse.title`): set `errorKey` to the shared constant and use that same value as the map key in `messages`.

**Adding a new tag:**
1. Add an entry to `Tags` with `personer` (and optional `rutineLink`)
2. If the error key is SCREAMING_SNAKE_CASE (SKE async errors): match via enum `name`, no `errorKey` needed
3. If the error key is a human-readable string: define/reuse a shared constant and set `errorKey` to it
4. No other changes needed — `lookupMap` and `sendMessage()` pick it up

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
- For SKE async errors: keep `Tags.name` identical to the SKE error `type` string
- For internally detected errors: set `errorKey` to the same shared constant used by `feilResponse.title`

### ⚠️ Ask first
- Removing / renaming existing `Tags` entries (on-call people depend on them)

### 🚫 Never
- Send Slack messages per individual error (rate limiting)
- Hardcode webhook URLs or user IDs outside `Tags` / config
- Log PII inside the Slack message body without checking it's safe for the channel
