package no.nav.sokos.ske.krav.config

import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.apache.http.impl.conn.SystemDefaultRoutePlanner
import java.net.ProxySelector

val httpClient =
    HttpClient(Apache) {
        expectSuccess = false

        install(HttpRequestRetry) {
            retryOnException(maxRetries = 3)
            delayMillis { retry -> retry * 3000L }
        }

        install(ContentNegotiation) {
            json(
                Json {
                    prettyPrint = true
                    ignoreUnknownKeys = true
                    encodeDefaults = true
                    explicitNulls = false
                },
            )
        }

        engine {
            customizeClient {
                setRoutePlanner(SystemDefaultRoutePlanner(ProxySelector.getDefault()))
            }
        }
    }
