# Testing Endre Krav

## Unit Tests (`EndreKravServiceTest`)

Uses `FunSpec` with parameterised test cases for status conforming logic:

```kotlin
data class TestCase(
    val description: String,
    val firstStatus: Int,
    val secondStatus: Int,
    val expectedFirstStatus: Status,
    val expectedSecondStatus: Status,
)
listOf(
    TestCase("404 and 422", 404, 422, Status.HTTP404_ANNEN_IKKE_FUNNET, Status.HTTP404_ANNEN_IKKE_FUNNET),
    TestCase("409 and 422", 409, 422, Status.HTTP422_VALIDERINGSFEIL, Status.HTTP422_VALIDERINGSFEIL),
    // ...
).forEach { ... }
```

Private methods are mocked via `spyk(EndreKravService(...), recordPrivateCalls = true)`.

## Integration Tests (`EndreKravServiceIntegrationTest`)

Uses `BehaviorSpec` with `DBListener` and SQL fixtures:

```kotlin
DBListener.loadInitScript("SQLscript/krav/TiNyeKrav.sql")
DBListener.loadInitScript("SQLscript/krav/ToEndredeKrav.sql")
```

Test fixture `ToEndredeKrav.sql` inserts 4 krav: 2 pairs of (ENDRING_RENTE + ENDRING_HOVEDSTOL) sharing `kravidentifikatorSKE`.

## Mock Endpoints

```kotlin
MockResponse(Endpoint.ENDRE_RENTER, nyEndringResponse(), HttpStatusCode.OK)
MockResponse(Endpoint.ENDRE_HOVEDSTOL, nyEndringResponse(), HttpStatusCode.OK)
```
