---
name: slack-alerting
description: SlackService feilakkumulering og Tags-basert varsling for prosesseringsfeil i SKE-batch
---

# Slack Alerting Skill

This skill covers the `SlackService` error accumulation pattern and `SlackClient` usage in this project.

## Design: Accumulate First, Send Once

Never call `SlackClient.sendMessage()` directly per error. Instead:

1. Call `slackService.addError()` one or more times to accumulate errors for a file/batch
2. Call `slackService.sendErrors()` once at the end to flush all accumulated errors

This prevents Slack rate-limiting and groups all errors for a file into a single message.

```kotlin
// Accumulate during processing
slackService.addError(fileName, "Feil i validering av fil", errorMessages)

// ... more processing ...

// Flush at the end (safe to call even if there are no errors)
slackService.sendErrors()
```

## SlackService API

```kotlin
class SlackService(
    private val slackClient: SlackClient = SlackClient(),
) {
    // Accumulate with a Map<errorType, List<message>>
    fun addError(
        fileName: String,
        header: String,
        messages: Map<String, List<String>>,
    )

    // Accumulate with a single Pair<errorType, message>
    fun addError(
        fileName: String,
        header: String,
        messages: Pair<String, String>,
    )

    // Accumulate with a List<Pair<errorType, message>>
    fun addError(
        fileName: String,
        header: String,
        messages: List<Pair<String, String>>,
    )

    // Send all accumulated errors to Slack, then clear the buffer
    suspend fun sendErrors()
}
```

Internal structure: `SlackService` maintains a `MutableList<FileErrors>` where each entry groups errors per file. `sendErrors()` iterates all file errors, calls `SlackClient.sendMessage()` per error header group, and clears the buffer.

## Tags — person tagging by error type

The `Tags` enum maps specific SKE error response types to on-call people. The `lookupMap` resolves an error type string from SKE's response to the correct `Tags` entry, which then provides a list of Slack user IDs and an optional routine link:

```kotlin
private enum class Tags(
    val personer: List<String>,
    val rutineLink: String? = null,
) {
    PERSON_EKSISTERER_IKKE(listOf(LENE, TRINE)),
    PERSON_ER_DOED(listOf(LENE, TRINE)),
    ORGANISASJONSNUMMER_FINNES_IKKE(listOf(LENE, TRINE)),
    ORGANISASJON_ER_OPPHOERT(
        listOf(MARITA, LINE_ANITA, STEINAR),
        "https://confluence.adeo.no/spaces/TOB/pages/791026050/Rutine+...",
    ),
    PERSON_ER_SLETTET(listOf(LENE, TRINE)),
    ORGANISASJON_ER_SLETTET(listOf(LENE, TRINE)),
    ;

    companion object {
        val lookupMap: Map<String, Tags> = entries.associateBy { it.name }
    }
}
```

To add a new error type that should tag specific people:
1. Add a new entry to `Tags` with the matching people and optional confluence link
2. The `lookupMap` resolves it automatically from the SKE error `type` field

## SlackClient

```kotlin
class SlackClient(
    private val slackEndpoint: String = PropertiesConfig.slackConfig.url,
    private val client: HttpClient = slackHttpClient,   // uses proxy-aware Apache5 client
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

The Slack webhook URL is provided via `slack.url` in `application.conf` (populated from `SOKOS_SKE_KRAV_SLACK_WEBHOOK_URL` env var). The `slackHttpClient` uses a proxy-aware engine (`SystemDefaultRoutePlanner`) — required to reach Slack from NAIS FSS.

## Mock in Tests

```kotlin
// SlackService is constructed with a no-op SlackClient in unit/integration tests:
val slackClient = MockHttpClient.slackClient   // returns HTTP 200 for all calls

// Or mock the whole SlackService with MockK:
val slackServiceMock = mockk<SlackService> {
    justRun { addError(any(), any(), any<List<Pair<String, String>>>()) }
    coJustRun { sendErrors() }
}
```

`MockHttpClient.slackClient` is a pre-built `HttpClient(MockEngine)` that responds `200 OK` to all requests — safe to use anywhere a `SlackClient` needs an HTTP client.

## Example: Adding a new error category

```kotlin
// 1. Add team members for the new error type in Tags:
ORGANISASJON_ER_KONKURS(listOf(MARITA, STEINAR)),

// 2. The error key from SKE's response body `type` field must match the enum name exactly.
//    sendErrors() will look it up in Tags.lookupMap and tag the right people automatically.
```

## HOCON config

```hocon
slack {
  url = ${SOKOS_SKE_KRAV_SLACK_WEBHOOK_URL}
}
```

