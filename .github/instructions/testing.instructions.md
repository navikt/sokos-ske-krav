---
applyTo: "**/test/**/*.kt"
---

# Testing essentials

Framework: **Kotest** (never JUnit) + **MockK**. Default spec style is `BehaviorSpec` (Given/When/Then/And) in Norwegian — both for unit and integration tests. Use `FunSpec` only for trivial, purely technical unit tests.

For full patterns, examples, and MockK/matchers cheat sheets, invoke the **`kotest` skill**.

## Hard rules

- Integration tests with DB → `extensions(DBListener)`; call `DBListener.clearDB()` before `loadInitScript(...)` inside every `Given`.
- Integration tests with SFTP → `extensions(SftpListener)`.
- Any test that reaches `SkeClient` (directly or via `MockHttpClient.client`) → `beforeEach { CircuitBreakerManager.circuitBreaker.reset() }`.
- Use `MockHttpClient.client(vararg MockResponse)` — never real HTTP to SKE.
- For suspend functions use `coEvery` / `coVerify`; never `runBlocking` inside test blocks.
- Never call `PropertiesConfig.load()` from tests — `DBListener` already does it.

## File conventions

- Unit tests: `.../service/unit/*Test.kt`
- Integration tests: `.../service/integration/*IntegrationTest.kt`, `.../validation/*IntegrationTest.kt`, `.../database/*Test.kt`
- SQL fixtures: `src/test/resources/SQLscript/krav/*.sql`

## Boundaries

### ✅ Always
- `BehaviorSpec` as default; Norwegian Given/When/Then text
- Reset circuit breaker in `beforeEach` for SKE-reaching tests
- Kotest matchers (`shouldBe`, `shouldHaveSize`, `shouldBeEmpty`, `with { ... }`)

### 🚫 Never
- JUnit
- Real HTTP to SKE
- `runBlocking` inside test blocks
- Leak mutable state between scenarios
