---
name: mottaksstatus-og-avstemming
description: "Post-send livssyklus: StatusService poller /mottaksstatus; StoppKravService; RapportService + AvstemmingRouting-frontend for manuell reskontroføring"
---

# Mottaksstatus, Stopp & Avstemming

After krav are sent to SKE, three subsystems handle the remaining lifecycle. This skill covers all three.

## StatusService — mottaksstatus polling

Runs on the scheduler (`launchJob(statusService::getMottaksStatus, ...)`). Picks up every krav with status in `{KRAV_SENDT, MOTTATT_UNDER_BEHANDLING, …}` and asks SKE's `/mottaksstatus/{kravidentifikatorSKE}` endpoint for the current state.

```kotlin
class StatusService(
    private val dataSource: HikariDataSource = PostgresDataSource.dataSource,
    private val skeClient: SkeClient = SkeClient(),
    private val databaseService: DatabaseService = DatabaseService(),
    private val slackService: SlackService = SlackService(),
) {
    suspend fun getMottaksStatus() {
        val kravListe = databaseService.getAllKravForStatusCheck()
        for (krav in kravListe) {
            runCatching { processKravStatus(krav) }.onFailure { break }  // stop on breaker open
        }
        slackService.sendErrors()
    }
}
```

Key points:
- **Stop-on-failure**: first HTTP failure breaks the loop — the circuit breaker is shared with the send path, so we don't hammer SKE when it's down.
- **Status mapping**: `MottaksStatusResponse.mottaksStatus` is one of `Status` enum values. Update krav row transactionally inside `asyncTransaction {}`.
- **Kravidentifikator**: use `createKravidentifikatorPair(krav)` to resolve `(identifikator, KravidentifikatorType)` — same helper used by Endre/Stopp.
- **Errors**: HTTP errors are decoded via `FeilResponse` / `ValideringsFeilResponse`, persisted via `FeilmeldingRepository`, and accumulated in `slackService` (flushed once at the end).

## StoppKravService — ytelse = 0

When a krav line has `beløp == 0`, it is classified as a stop. `StoppKravService.sendAllStoppKrav(list)` mirrors `OpprettKravService` but calls `skeClient.stoppKrav()` (PUT `/avskriving`).

Same circuit-breaker-break-out pattern as the other send services:

```kotlin
for (krav in kravList) {
    runCatching { requestResults.add(sendStoppKrav(krav)) }.onFailure { e ->
        if (e is CircuitBreakerException || e is CallNotPermittedException) break
        else throw e
    }
}
databaseService.updateSentKrav(requestResults)
```

Classification (`KravLinje.isStopp() = belop.toDouble().roundToLong() == 0L`) is shared with `CreateRequests.kt`. No DB-row doubling (unlike endring).

## Avstemming / Rapport — manual reconciliation UI

Some krav end up in states that need human follow-up (e.g. `HTTP400_*`, stuck in `MOTTATT_UNDER_BEHANDLING` for too long). `RapportService` + `AvstemmingRouting` expose an authenticated HTML page for operators.

### `@Frontend` opt-in

```kotlin
@RequiresOptIn(message = "Skal bare brukes i frontend")
@Retention(AnnotationRetention.BINARY)
annotation class Frontend

@Frontend
enum class RapportType { AVSTEMMING, RESENDING }

@Frontend
class RapportService(
    private val dataSource: HikariDataSource = PostgresDataSource.dataSource,
    private val dbService: DatabaseService = DatabaseService(),
) {
    val kravSomSkalAvstemmes by lazy { mapToRapportObjekt(dbService.getAllKravForAvstemming()) }
    val kravSomSkalResendes by lazy { mapToRapportObjekt(dbService.getAllKravForResending()) }

    fun oppdaterStatusTilRapportert(kravId: Int) =
        dbService.updateStatusForAvstemtKravToReported(kravId)
}
```

All frontend-only code is annotated `@Frontend` and callers need `@OptIn(Frontend::class)`. This keeps UI-only APIs from leaking into the batch pipeline.

### Routes

```kotlin
@OptIn(Frontend::class)
fun Route.avstemmingRoutes(rapportService: RapportService = RapportService()) {
    staticResources("/static", "static")
    route("rapporter") {
        route("avstemming") {
            get { call.respondHtmlTemplate(RapportTemplate(RapportType.AVSTEMMING)) { /* ... */ } }
            post("/update") {
                val id = call.receiveParameters()["kravid"]
                if (!id.isNullOrBlank()) rapportService.oppdaterStatusTilRapportert(id.toInt())
                call.respondRedirect("/rapporter/avstemming")
            }
            post("/CSVdownload") { /* echo CSV back */ }
        }
    }
}
```

Templates live in `frontend/*Template.kt` (Ktor `kotlinx.html` DSL). `AvstemmingTemplate` renders the full operator page; `RapportTemplate` and `ResendingTemplate` share the outer layout.

### Flow

```
Operator opens /rapporter/avstemming (Basic auth required)
  → RapportService.kravSomSkalAvstemmes
  → AvstemmingTemplate renders table with one row per krav
Operator clicks "Rapportert"
  → POST /rapporter/avstemming/update with kravid
  → RapportService.oppdaterStatusTilRapportert(kravId)
  → DatabaseService marks krav as reported (status = AVSTEMT_OG_RAPPORTERT)
```

## Boundaries

### ✅ Always
- Break the `getMottaksStatus` / `sendAllStoppKrav` loop on `CircuitBreakerException` / `CallNotPermittedException`
- Flush `slackService.sendErrors()` exactly once at the end of each run
- Annotate any new frontend-only API with `@Frontend` and require `@OptIn(Frontend::class)` at call sites
- Resolve kravidentifikator via `createKravidentifikatorPair(krav)`

### ⚠️ Ask first
- Adding new RapportType entries (affects DB query + template)
- Changing which statuses are picked up by `getAllKravForStatusCheck` / `getAllKravForAvstemming`

### 🚫 Never
- Call frontend APIs from the batch path (opt-in would require leaking `@Frontend` out)
- Update krav status outside a transaction
- Log gjelderId / saksnummer without `TEAM_LOGS_MARKER`
