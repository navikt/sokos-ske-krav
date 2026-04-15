---
name: maskinporten
description: Maskinporten JWT-assertion flow med token-caching via AtomicReference og Mutex for server-til-server autentisering mot SKE API
---

# Maskinporten Authentication Skill

This skill covers the `MaskinportenAccessTokenProvider` pattern used in this project for server-to-server authentication against SKE's REST API.

## How It Works

Maskinporten uses the **JWT Bearer Grant** flow:
1. Build a signed JWT assertion (client ID, audience, scope, expiry, unique `jti`)
2. POST it to Maskinporten's token endpoint as `grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer`
3. Cache the returned `access_token` until 60 seconds before its expiry
4. Attach the token as `Authorization: Bearer <token>` on every SKE API call

## MaskinportenAccessTokenProvider

```kotlin
class MaskinportenAccessTokenProvider(
    private val maskinportenConfig: MaskinportenClientConfig = PropertiesConfig.maskinportenClientProperties,
    private val client: HttpClient,
) {
    private val mutex = Mutex()
    private val timeLimit = Duration.ofSeconds(60)

    private data class AccessToken(
        val token: String,
        val expiresAt: Instant,
    ) {
        constructor(response: MaskinportenTokenResponse) :
            this(response.accessToken, Instant.now().plusSeconds(response.expiresIn))
    }

    private val tokenCache = AtomicReference<AccessToken?>(null)

    suspend fun getAccessToken(): String =
        mutex.withLock {
            val nowPlusLimit = Instant.now().plus(timeLimit)
            val cached = tokenCache.get()
            if (cached == null || cached.expiresAt < nowPlusLimit) {
                val newToken = getMaskinportenToken()
                tokenCache.set(newToken)
                newToken.token
            } else {
                cached.token
            }
        }

    private suspend fun getMaskinportenToken(): AccessToken {
        val openIdConfig = client.get(maskinportenConfig.wellKnownUrl).body<OpenIdConfiguration>()
        val assertion = createJwtAssertion(openIdConfig.issuer)

        val response = client.submitForm(
            url = openIdConfig.tokenEndpoint,
            formParameters = Parameters.build {
                append("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer")
                append("assertion", assertion)
            },
        )

        val body = response.bodyAsText()
        val tokenResponse = body.decodeTo<MaskinportenTokenResponse>()
        return if (tokenResponse != null) {
            AccessToken(tokenResponse)
        } else {
            logger.error("Kunne ikke hente accessToken")
            logger.error(marker = TEAM_LOGS_MARKER) {
                "Feil fra tokenprovider, Token: $assertion, Feilmelding: ${body.decodeTo<TokenError>()}"
            }
            throw Exception("Feil fra tokenprovider: $body")
        }
    }

    private fun createJwtAssertion(issuer: String): String =
        JWT.create()
            .withIssuer(maskinportenConfig.clientId)
            .withAudience(issuer)
            .withClaim("scope", maskinportenConfig.scopes)
            .withExpiresAt(Date(Instant.now().plus(timeLimit).toEpochMilli()))
            .withIssuedAt(Date())
            .withKeyId(maskinportenConfig.rsaKey?.keyID)
            .withJWTId(UUID.randomUUID().toString())
            .sign(Algorithm.RSA256(null, maskinportenConfig.rsaKey?.toRSAPrivateKey()))
}
```

### Key design decisions

| Decision | Reason |
|---|---|
| `AtomicReference<AccessToken?>` | Lock-free read for the common case (token is still valid) |
| `Mutex.withLock {}` | Prevents thundering-herd: only one coroutine fetches a new token at a time |
| Renew 60 s before expiry | Avoids 401s from clock skew between this service and Maskinporten |
| `jti = UUID.randomUUID()` | Required by Maskinporten to prevent replay attacks |

## Config Data Class

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

HOCON keys (`application.conf`):
```hocon
maskinportenClient {
  clientId    = ${MASKINPORTEN_CLIENT_ID}
  wellKnownUrl = ${MASKINPORTEN_WELL_KNOWN_URL}
  rsaKeyString = ${MASKINPORTEN_CLIENT_JWK}
  scopes       = ${MASKINPORTEN_SCOPES}
}
```

## Attaching the Token in SkeClient

```kotlin
private suspend fun doPost(
    path: String,
    body: Any,
    corrID: String,
): HttpResponse {
    val token = tokenProvider.getAccessToken()
    return client.post {
        url("$skeEndpoint$path")
        headers {
            append(HttpHeaders.Authorization, "Bearer $token")
            append("x-correlation-id", corrID)
            append("klient-id", KLIENT_ID)
        }
        contentType(ContentType.Application.Json)
        setBody(body)
    }
}
```

## Mocking in Tests

Pass `mockk<MaskinportenAccessTokenProvider>(relaxed = true)` — all calls to `getAccessToken()` will return an empty string by default, which is fine for tests using `MockHttpClient` (the mock engine ignores the `Authorization` header):

```kotlin
val skeClient = SkeClient(
    skeEndpoint = "",
    client = MockHttpClient.client(MockResponse(Endpoint.OPPRETT, body, HttpStatusCode.OK)),
    tokenProvider = mockk<MaskinportenAccessTokenProvider>(relaxed = true),
)
```

## Logging Rules

- Log token exchange errors with `TEAM_LOGS_MARKER` (the JWT assertion contains sensitive key material)
- Never log the raw `access_token` value at any log level

