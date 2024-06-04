package sokos.ske.krav.config

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.plugins.callid.callIdMdc
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.path
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics
import io.micrometer.core.instrument.binder.system.ProcessorMetrics
import io.micrometer.core.instrument.binder.system.UptimeMetrics
import io.prometheus.client.exporter.common.TextFormat
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import org.slf4j.event.Level
import sokos.ske.krav.metrics.Metrics
import java.util.*

private val logger = KotlinLogging.logger { }
@OptIn(ExperimentalSerializationApi::class)
fun Application.commonConfig(
	configuratuion: Configuration
) {

  install(CallId) {
	header("nav-call-id")
	generate { UUID.randomUUID().toString() }
	verify { it.isNotEmpty() }
  }
  install(CallLogging) {
	level = Level.INFO
	callIdMdc(HttpHeaders.XCorrelationId)
	filter { call -> call.request.path().startsWith("/krav") }
	disableDefaultColors()
  }
  install(ContentNegotiation) {
	json(Json {
	  prettyPrint = true
	  isLenient = true
	  explicitNulls = false
	})
  }
	install(Authentication) {
		jwt {
			verifier(configuratuion.azureAd.jwkProvider, configuratuion.azureAd.openIdConfiguration.issuer)
			realm = configuratuion.appName
			validate { cred ->
				try {
					requireNotNull(cred.payload.audience) { "Ikke gyldig Token, mangler audience" }
					require(cred.payload.audience.contains(configuratuion.azureAd.clientId)) { "Ikke gyldig Token, ikke gyldig audienceClaim" }
					JWTPrincipal(cred.payload)
				} catch (e: Exception) {
					logger.warn { e.message }
					null
				}
			}
		}
	}

  install(MicrometerMetrics) {
	registry = Metrics.registry
	meterBinders = listOf(
	  UptimeMetrics(),
	  JvmMemoryMetrics(),
	  JvmGcMetrics(),
	  JvmThreadMetrics(),
	  ProcessorMetrics(),
	)
  }
  routing {
	route("internal/metrics") {
	  get {
		call.respondText(ContentType.parse(TextFormat.CONTENT_TYPE_004)) { Metrics.registry.scrape() }
	  }
	}
  }
}
