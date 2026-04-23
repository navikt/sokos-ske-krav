---
name: endre-krav
description: "Endring av eksisterende krav (endre rente og endre hovedstol): EndreKravService, request-DTOer, status-konformering og SKE API PUT-endepunkter"
---

# Endre Krav — Rente & Hovedstol

Endring av eksisterende krav hos SKE: EndreKravService, NyHovedStolRequest, EndreRenteBeloepRequest, status-konformering og kravidentifikator-oppslag.

> When a krav line has a non-empty `referansenummerGammelSak` and `beløp != 0`, it is classified as an "endring". Two separate DB records are created — one `ENDRING_HOVEDSTOL` and one `ENDRING_RENTE` — and two separate PUT requests are sent to SKE's API.

## How Endring Is Detected

In `CreateRequests.kt` / `KravLinje` extensions:

```kotlin
fun KravLinje.isEndring() = (referansenummerGammelSak.isNotEmpty() && !isStopp())
fun KravLinje.isStopp() = (belop.toDouble().roundToLong() == 0L)
fun KravLinje.isOpprettKrav() = (!this.isEndring() && !this.isStopp())
```

**Decision logic:** `referansenummerGammelSak` present → endring. Beløp == 0 → stopp. Otherwise → nytt krav.

## Database: Two Records Per Endring

When `KravRepository.insertAllNewKrav()` encounters an endring line, it inserts **two** krav rows:

```kotlin
kravListe.forEach { krav ->
    val type = when {
        krav.isStopp() -> STOPP_KRAV
        krav.isEndring() -> ENDRING_HOVEDSTOL   // first record
        else -> NYTT_KRAV
    }
    // ... insert krav with type ...

    if (type == ENDRING_HOVEDSTOL) {
        // insert SECOND krav with kravtype = ENDRING_RENTE (same data)
    }
}
```

Both records share the same `kravidentifikatorSKE` and `saksnummerNAV` but have different `kravtype` and may have different `corrId`.

## Processing Flow

```
Input File (KravLinje with referansenummerGammelSak)
  ↓
KravRepository.insertAllNewKrav()
  ├─ Insert krav #1: kravtype = ENDRING_HOVEDSTOL
  └─ Insert krav #2: kravtype = ENDRING_RENTE
  ↓
SkeService.sendKrav() → filter ENDRING_HOVEDSTOL | ENDRING_RENTE
  ↓
EndreKravService.sendAllEndreKrav()
  ├─ Group by: kravidentifikatorSKE + saksnummerNAV
  ├─ For each group:
  │   ├─ sendEndreKrav(ENDRING_HOVEDSTOL) → PUT .../hovedstol
  │   ├─ sendEndreKrav(ENDRING_RENTE) → PUT .../renter
  │   └─ getConformedResponses() → align statuses
  └─ databaseService.updateSentKrav()
```

## Sub-files

- See [request-dtos.md](request-dtos.md) for kravtype constants, request DTOs, request creation, KravidentifikatorType lookup, and SkeClient endpoints.
- See [service.md](service.md) for EndreKravService orchestration, sending individual requests, status conforming, and SkeService orchestration.
- See [testing.md](testing.md) for unit tests, integration tests, and mock endpoints.

## Boundaries

### ✅ Always

- Create two DB records per endring line (ENDRING_HOVEDSTOL + ENDRING_RENTE)
- Group krav by `kravidentifikatorSKE + saksnummerNAV` before sending
- Conform statuses across grouped requests
- Use `createKravidentifikatorPair()` for identifier resolution
- Break out of loop on `CircuitBreakerException` / `CallNotPermittedException`
- Reset circuit breaker in `beforeEach` of tests

### ⚠️ Ask First

- Changing the status conforming priority order
- Adding new endring types beyond hovedstol/rente
- Changing the grouping logic

### 🚫 Never

- Send endring requests without grouping (each pair must be conformed)
- Skip the second record (ENDRING_RENTE) when inserting endring krav
- Hardcode kravidentifikator lookup — always use `createKravidentifikatorPair()`
