package sokos.ske.krav.metrics

import io.ktor.http.ContentType
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics
import io.micrometer.core.instrument.binder.system.ProcessorMetrics
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import io.prometheus.client.exporter.common.TextFormat
import sokos.ske.krav.domain.ske.requests.TilleggsinformasjonNav.StoenadsType

object Metrics {
    val registry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

    fun typeKravSendt(kode: String): Counter = Counter.builder("krav.type")
        .tag("Kravtype", kode)
        .register(registry)

    val appStateRunningFalse: Counter = Counter.builder("app.state.running.false")
        .description("App state running changed to false.")
        .register(registry)

    val appStateReadyFalse: Counter = Counter.builder("app.state.ready.false")
        .description("App state ready changed to false.")
        .register(registry)

    val antallKravSendt: Counter = Counter.builder("krav.sendt")
        .description("antall krav sendt til endepunkt")
        .register(registry)

    val antallKravLest: Counter = Counter.builder("krav.lest")
        .description("antall krav Lest fra fil")
        .register(registry)

    val apiKallTimer: (String, StoenadsType) -> Timer = { url, stonad ->
        Timer.builder("api.kall")
            .description("Api call timer")
            .tags(
                "url",
                url,
                "stonadstype",
                stonad.value,
            ).register(registry)
    }
}

fun Application.installMetrics() {
    install(MicrometerMetrics) {
        registry = Metrics.registry
        meterBinders = listOf(
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
