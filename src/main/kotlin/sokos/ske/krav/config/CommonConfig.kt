package sokos.ske.krav.config

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.metrics.micrometer.*
import io.ktor.server.plugins.callid.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics
import io.micrometer.core.instrument.binder.system.ProcessorMetrics
import io.micrometer.core.instrument.binder.system.UptimeMetrics
import io.prometheus.client.exporter.common.TextFormat
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import org.slf4j.event.Level
import sokos.ske.krav.metrics.Metrics
import java.util.*

@OptIn(ExperimentalSerializationApi::class)
fun Application.commonConfig() {

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
