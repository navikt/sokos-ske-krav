package sokos.ske.krav.config

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.plugins.callid.callIdMdc
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.path
import io.ktor.server.response.respondText
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics
import io.micrometer.core.instrument.binder.system.ProcessorMetrics
import io.micrometer.core.instrument.binder.system.UptimeMetrics
import kotlinx.serialization.json.Json
import org.slf4j.event.Level
import sokos.ske.krav.ApplicationState
import sokos.ske.krav.metrics.Metrics
import java.util.UUID

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
        json(
            Json {
                prettyPrint = true
                isLenient = true
                explicitNulls = false
            },
        )
    }

    install(MicrometerMetrics) {
        registry = Metrics.registry
        meterBinders =
            listOf(
                UptimeMetrics(),
                JvmMemoryMetrics(),
                JvmGcMetrics(),
                JvmThreadMetrics(),
                ProcessorMetrics(),
            )
    }
}

fun Routing.internalNaisRoutes(
    applicationState: ApplicationState,
    readinessCheck: () -> Boolean = { applicationState.ready },
    alivenessCheck: () -> Boolean = { applicationState.alive },
) {
    route("internal") {
        get("isAlive") {
            healthCheckResponse(alivenessCheck(), call, "I'm alive :)", "I'm dead x_x")
        }
        get("isReady") {
            healthCheckResponse(readinessCheck(), call, "I'm ready! :)", "Wait! I'm not ready yet! :O")
        }
    }
    get("metrics") {
        call.respondText(Metrics.registry.scrape())
    }
}

private val healthCheckResponse: suspend (Boolean, ApplicationCall, String, String) -> Unit =
    { isHealthy, call, successMessage, failureMessage ->
        when (isHealthy) {
            true -> call.respondText { successMessage }
            else -> call.respondText(failureMessage, status = HttpStatusCode.InternalServerError)
        }
    }
