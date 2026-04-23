# EndreKravService

## Orchestration

```kotlin
class EndreKravService(
    private val skeClient: SkeClient,
    private val databaseService: DatabaseService,
) {
    suspend fun sendAllEndreKrav(kravList: List<Krav>): List<RequestResult> {
        // 1. Group by kravidentifikatorSKE + saksnummerNAV
        val kravGroupedByIdentifier = kravList.groupBy { it.kravidentifikatorSKE + it.saksnummerNAV }

        // 2. Process each group (typically 2 krav: 1 HOVEDSTOL + 1 RENTE)
        val requestResults = mutableListOf<RequestResult>()
        for ((_, groupedKrav) in kravGroupedByIdentifier) {
            runCatching { requestResults.addAll(processKravGroup(groupedKrav)) }.onFailure { e ->
                if (e is CircuitBreakerException || e is CallNotPermittedException) break
                else throw e
            }
        }

        // 3. Persist results
        databaseService.updateSentKrav(requestResults)
        return requestResults
    }
}
```

## Sending Individual Requests

```kotlin
private suspend fun sendEndreKrav(
    kravidentifikator: String,
    kravidentifikatorType: KravidentifikatorType,
    krav: Krav,
): RequestResult {
    val (response, request) =
        if (krav.kravtype == ENDRING_RENTE) {
            val request = createEndreRenteRequest(krav)
            val response = skeClient.endreRenter(request, kravidentifikator, kravidentifikatorType, krav.corrId)
            Pair(response, request.encodeToString())
        } else {
            val request = createEndreHovedstolRequest(krav)
            val response = skeClient.endreHovedstol(request, kravidentifikator, kravidentifikatorType, krav.corrId)
            Pair(response, request.encodeToString())
        }
    // ... map response to RequestResult with defineStatus()
}
```

## Status Conforming

When two requests in a group have **different** HTTP status codes, `getConformedResponses()` picks the highest-priority status and applies it to both:

**Priority order:** 404 (NotFound) > 422 (UnprocessableEntity) > 409 (Conflict) > other → `UKJENT_STATUS`

```kotlin
private fun determineNewStatus(
    endring1: Pair<Int, Status>,
    endring2: Pair<Int, Status>,
): Status = when {
    endring1.first == HttpStatusCode.NotFound.value -> endring1.second
    endring2.first == HttpStatusCode.NotFound.value -> endring2.second
    endring1.first == HttpStatusCode.UnprocessableEntity.value -> endring1.second
    endring2.first == HttpStatusCode.UnprocessableEntity.value -> endring2.second
    endring1.first == HttpStatusCode.Conflict.value -> endring1.second
    endring2.first == HttpStatusCode.Conflict.value -> endring2.second
    else -> Status.UKJENT_STATUS
}
```

This ensures both krav in a group (hovedstol + rente) end up with the same status.

## SkeService Orchestration

```kotlin
// SkeService.sendKrav()
val allResponses =
    opprettKravService.sendAllOpprettKrav(kravList.filter { it.kravtype == NYTT_KRAV }) +
    endreKravService.sendAllEndreKrav(kravList.filter { it.kravtype == ENDRING_HOVEDSTOL || it.kravtype == ENDRING_RENTE }) +
    stoppKravService.sendAllStoppKrav(kravList.filter { it.kravtype == STOPP_KRAV })
```
