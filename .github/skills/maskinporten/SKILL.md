---
name: maskinporten
description: "Maskinporten JWT-assertion flow med AtomicReference + Mutex token-caching for server-til-server autentisering mot SKE"
---

# Maskinporten authentication

`MaskinportenAccessTokenProvider` performs the JWT Bearer Grant flow against Maskinporten and caches the access token in memory until just before expiry. Used by `SkeClient` on every call.

## Flow

1. Fetch `openid-configuration` from `wellKnownUrl` → `tokenEndpoint`, `issuer`
2. Build a signed JWT assertion (RSA256, `iss = clientId`, `aud = issuer`, `scope`, `exp`, random `jti`)
3. POST as `grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer` → receive `access_token`
4. Cache until 60 s before expiry; re-fetch on expiry (single-flight via `Mutex`)
5. Attach as `Authorization: Bearer <token>` on every SKE call

## Key design

| Decision | Reason |
|---|---|
| `AtomicReference<AccessToken?>` | Lock-free read in the common path (cache hit) |
| `Mutex.withLock {}` around refresh | Prevents thundering-herd when many coroutines race to refresh |
| Renew 60 s before expiry | Guards against clock skew vs. Maskinporten |
| `jti = UUID.randomUUID()` | Required by Maskinporten to prevent replay |
| `scopes` passed through HOCON | Multiple scopes go as space-separated string |

## Config

```hocon
maskinportenClient {
  clientId     = ${MASKINPORTEN_CLIENT_ID}
  wellKnownUrl = ${MASKINPORTEN_WELL_KNOWN_URL}
  rsaKeyString = ${MASKINPORTEN_CLIENT_JWK}
  scopes       = ${MASKINPORTEN_SCOPES}
}
```

```kotlin
@Serializable
data class MaskinportenClientConfig(
    val clientId: String,
    val wellKnownUrl: String,
    val rsaKeyString: String,
    val scopes: String,
) {
    val rsaKey: RSAKey? by lazy { RSAKey.parse(rsaKeyString) }
}
```

## Contract

```kotlin
class MaskinportenAccessTokenProvider(
    private val maskinportenConfig: MaskinportenClientConfig = PropertiesConfig.maskinportenClientProperties,
    private val client: HttpClient,
) {
    suspend fun getAccessToken(): String       // the only public method
}
```

Both parameters are injectable, so tests pass `mockk(relaxed = true)`.

## In SkeClient

`SkeClient` calls `tokenProvider.getAccessToken()` before every request and appends:

```kotlin
headers {
    append(HttpHeaders.Authorization, "Bearer $token")
    append("x-correlation-id", corrID)
    append("klient-id", KLIENT_ID)
}
```

## Tests

Maskinporten is always mocked — tests never reach the real endpoint:

```kotlin
val skeClient = SkeClient(
    skeEndpoint = "",
    client = MockHttpClient.client(MockResponse(Endpoint.OPPRETT, body, HttpStatusCode.OK)),
    tokenProvider = mockk<MaskinportenAccessTokenProvider>(relaxed = true),
)
```

The relaxed mock returns `""` from `getAccessToken()` — fine since `MockEngine` ignores headers.

## Boundaries

### ✅ Always
- Use `PropertiesConfig.maskinportenClientProperties` for config
- Log token-provider errors with `TEAM_LOGS_MARKER` (JWT assertion contains key material)
- Mock `MaskinportenAccessTokenProvider` in tests

### 🚫 Never
- Log `access_token`, `assertion`, or raw JWKs at any level
- Skip the `Mutex` around the refresh block
- Cache beyond `expiresIn - 60s`
