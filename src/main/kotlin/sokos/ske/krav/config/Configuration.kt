package sokos.ske.krav.config

import com.auth0.jwk.JwkProvider
import com.auth0.jwk.JwkProviderBuilder
import com.fasterxml.jackson.annotation.JsonProperty
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import sokos.ske.krav.util.httpClient
import java.net.URI
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger {  }

private fun readProperty(name: String, default: String? = null) =
    System.getenv(name)
        ?: System.getProperty(name)
        ?: default.takeIf { it != null }?.also { logger.warn { "Using default value for property $name" } }
        ?: throw RuntimeException("Mandatory property '$name' was not found")
