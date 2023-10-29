package sokos.ske.krav.metrics

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.metrics.micrometer.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics
import io.micrometer.core.instrument.binder.system.ProcessorMetrics
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import io.prometheus.client.exporter.common.TextFormat

object Metrics {
    val registry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

    val appStateRunningFalse: Counter = Counter.builder("app.state.running.false")
        .description("App state running changed to false.")
        .register(registry)

    val appStateReadyFalse: Counter = Counter.builder("app.state.ready.false")
        .description("App state ready changed to false.")
        .register(registry)

    val appStateReadyTrue: Counter = Counter.builder("app.state.ready.true")
        .description("App state is ready ")
        .register(registry)

}

fun Application.installMetrics() {
    install(MicrometerMetrics) {
        registry = Metrics.registry
        meterBinders = listOf(
            JvmMemoryMetrics(),
            JvmGcMetrics(),
            JvmThreadMetrics(),
            ProcessorMetrics()
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
