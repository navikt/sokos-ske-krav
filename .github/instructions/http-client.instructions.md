---
applyTo: "**/client/**/*.kt,**/config/Circuit*.kt,**/config/HttpClient*.kt"
---

# HTTP client & circuit breaker

Shared `httpClient` is Ktor + Apache5 engine with `CircuitBreakerPlugin` installed and `SystemDefaultRoutePlanner` (proxy-aware, required on NAIS FSS). Every SKE request goes through `CircuitBreakerManager.guardCall {}` via an `HttpSend` interceptor.

When the breaker opens, the `sendAll*` loops in `OpprettKravService` / `EndreKravService` / `StoppKravService` break out — no further krav are sent in the batch.

`SkeClient(skeEndpoint, client, tokenProvider)` injects all three dependencies so tests can swap in `MockHttpClient.client(...)` and a relaxed `MaskinportenAccessTokenProvider` mock.

For Maskinporten token caching see the `maskinporten` skill.

## Boundaries

### ✅ Always
- Wrap SKE HTTP calls with `CircuitBreakerManager.guardCall {}`
- Reset `CircuitBreakerManager.circuitBreaker` in `beforeEach` of tests that reach `SkeClient`
- Use the proxy-aware route planner

### ⚠️ Ask first
- Tuning circuit-breaker thresholds/timeouts
- Adding new Maskinporten scopes

### 🚫 Never
- Bypass the circuit breaker for SKE calls
- Hardcode authentication tokens or private keys
