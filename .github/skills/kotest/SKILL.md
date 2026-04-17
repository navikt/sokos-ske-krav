---
name: kotest
description: "Kotest BehaviorSpec/MockK-mønstre for sokos-ske-krav: scenariostruktur, DBListener/SftpListener, MockHttpClient, circuit-breaker-reset og matchers"
---

# Kotest patterns

Default spec style: **`BehaviorSpec`** (Given/When/Then/And) in Norwegian. Use `FunSpec` only for trivial, non-scenario-based unit tests.

## Canonical BehaviorSpec

```kotlin
class OpprettKravServiceTest : BehaviorSpec({
    val databaseServiceMock = mockk<DatabaseService> {
        justRun { updateSentKrav(any<List<RequestResult>>()) }
    }

    Given("et nytt krav som skal sendes") {
        val krav = mockk<Krav>(relaxed = true) {
            every { corrId } returns "test-corr-id"
            every { kravkode } returns "BA OR"
        }

        When("SKE svarer med 200 OK") {
            val skeClientMock = mockk<SkeClient> {
                coEvery { opprettKrav(any(), any()) } returns
                    mockHttpResponse(body = MockResponsesBody.nyttKravResponse("123"))
            }
            val service = OpprettKravService(skeClientMock, databaseServiceMock)

            val results = service.sendAllOpprettKrav(listOf(krav))

            Then("skal det returneres ett RequestResult med status KRAV_SENDT") {
                results.shouldHaveSize(1)
                results.first().status shouldBe Status.KRAV_SENDT
                results.first().kravidentifikator shouldBe "123"
            }
            And("DatabaseService skal oppdatere status") {
                verify(exactly = 1) { databaseServiceMock.updateSentKrav(any<List<RequestResult>>()) }
            }
        }
    }
})
```

Conventions:
- Norwegian Given/When/Then/And text
- Testdata in `Given`, scenario-specific mocks in `When`, shared fixtures on spec top-level
- One `When` per causal step; multiple `Then`/`And` for independent assertions
- Never `runBlocking` inside test blocks — use `coEvery` / `coVerify`

## DB integration (`DBListener`)

```kotlin
internal class OpprettKravServiceIntegrationTest : BehaviorSpec({
    extensions(DBListener)
    beforeEach { CircuitBreakerManager.circuitBreaker.reset() }

    val dbService = DatabaseService(DBListener.dataSource)

    Given("2 nye krav som ikke er sendt") {
        DBListener.clearDB()
        DBListener.loadInitScript("SQLscript/krav/ToNyeKrav.sql")

        val krav = dbService.getAllUnsentKrav()
        krav.shouldHaveSize(2)

        When("SKE svarer med OK") {
            val httpClient = MockHttpClient.client(
                MockResponse(Endpoint.OPPRETT, nyttKravResponse("4321"), HttpStatusCode.OK),
            )
            val skeClient = SkeClient("", httpClient, mockk(relaxed = true))
            val results = OpprettKravService(skeClient, dbService).sendAllOpprettKrav(krav)

            Then("alle krav skal være sendt") {
                results.shouldHaveSize(2)
                dbService.getAllUnsentKrav().shouldBeEmpty()
            }
        }
    }
})
```

| DBListener helper | Purpose |
|---|---|
| `extensions(DBListener)` | Registers TestContainers PostgreSQL 16 |
| `DBListener.dataSource` | `HikariDataSource` with Flyway migrations applied |
| `DBListener.clearDB()` | `TRUNCATE ... RESTART IDENTITY CASCADE` (also runs `afterSpec`) |
| `DBListener.loadInitScript("SQLscript/...")` | Loads fixture from `src/test/resources/` |

Always `clearDB()` before `loadInitScript(...)` inside each `Given` — listener state leaks between scenarios otherwise.

## SFTP integration

```kotlin
internal class FtpServiceIntegrationTest : BehaviorSpec({
    extensions(SftpListener)
    // ...
})
```

## HTTP mocking

`MockHttpClient.client(vararg MockResponse)` matches by endpoint path and installs `CircuitBreakerPlugin` — so tests exercise production wiring.

```kotlin
val client = MockHttpClient.client(
    MockResponse(Endpoint.OPPRETT, nyttKravResponse("id-123"), HttpStatusCode.OK),
    MockResponse(Endpoint.MOTTAKSSTATUS, mottaksStatusResponse(), HttpStatusCode.OK),
)
```

For unit tests that don't need HTTP wiring, mock `SkeClient` directly:

```kotlin
val skeClientMock = mockk<SkeClient> {
    coEvery { opprettKrav(any(), any()) } returns mockHttpResponse(body = nyttKravResponse("123"))
}
```

## MockK cheat sheet

| Need | Pattern |
|---|---|
| Plain mock | `mockk<T>()` |
| Relaxed (auto-stub everything) | `mockk<T>(relaxed = true)` |
| Spy on real instance | `spyk(MyService(...), recordPrivateCalls = true)` |
| Suspend stub | `coEvery { ... } returns ...` |
| `Unit`-returning stub | `justRun { ... }` / `coJustRun { ... }` |
| Verify | `verify(exactly = n) { ... }` / `coVerify { ... }` |
| Private function stub | `every { spy["privateFun"](any<T>()) } returns ...` (needs `recordPrivateCalls = true`) |

## Assertions

Prefer Kotest matchers. Group related field checks with `with { ... }`:

```kotlin
result shouldBe expected
list.shouldHaveSize(2)
list.shouldBeEmpty()
list.shouldContainExactly(a, b)
exception.message shouldContain "fagsystemId"

with(result.first()) {
    httpStatusCode shouldBe HttpStatusCode.OK
    status shouldBe Status.KRAV_SENDT
    kravidentifikator shouldBe "123"
}
```

## Circuit breaker

Every test that eventually calls `SkeClient` must reset the breaker — otherwise state leaks between tests:

```kotlin
beforeEach { CircuitBreakerManager.circuitBreaker.reset() }
```

When an open breaker is expected (e.g. multiple `Forbidden` responses), verify with `coVerify(exactly = 1) { ... }` that further calls were suppressed.

## Boundaries

### ✅ Always
- `BehaviorSpec` as default; Norwegian scenario text
- Reset circuit breaker in `beforeEach` for SKE-reaching tests
- `DBListener.clearDB()` before every `loadInitScript(...)`
- `MockHttpClient.client(...)` for HTTP
- Kotest matchers (`shouldBe`, `shouldHaveSize`, …)
- `coEvery` / `coVerify` for suspend functions

### ⚠️ Ask first
- New global test listeners or base specs
- Tests requiring real network or real SFTP

### 🚫 Never
- JUnit
- `runBlocking { ... }` in test blocks
- Real HTTP to SKE
- `PropertiesConfig.load()` in a test (DBListener already did it)
- Share mutable state between scenarios without explicit reset
