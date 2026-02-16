package no.nav.sokos.ske.krav.config

import java.net.ProxySelector

import kotlinx.serialization.json.Json

import io.ktor.client.HttpClient
import io.ktor.client.engine.apache5.Apache5
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import org.apache.hc.client5.http.impl.routing.SystemDefaultRoutePlanner

private val logger = mu.KotlinLogging.logger {}

val jsonConfig =
    Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }
val httpClient =
    HttpClient(Apache5) {
        expectSuccess = false

        HttpResponseValidator {
            validateResponse { response ->
                when (response.status) {
                    HttpStatusCode.Forbidden, HttpStatusCode.InternalServerError -> {
                        logger.error("Received error status ${response.status}")
                    }

                    else -> {}
                }
            }
        }

        install(HttpRequestRetry) {
            retryOnException(maxRetries = 3)
            delayMillis { retry -> retry * 3000L }
        }

        install(ContentNegotiation) {
            json(
                jsonConfig,
            )
        }

        engine {
            customizeClient {
                setRoutePlanner(SystemDefaultRoutePlanner(ProxySelector.getDefault()))
            }
        }
    }
