package no.nav.sokos.ske.krav.util

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.instrumentation.api.incubator.log.LoggingContextConstants
import org.slf4j.MDC

object TraceUtils {
    private val openTelemetry = GlobalOpenTelemetry.get()

    suspend fun <T> withTracerId(
        tracer: Tracer = openTelemetry.getTracer(this::class.java.canonicalName),
        spanName: String = "withTracerId",
        block: suspend () -> T,
    ): T {
        val span = tracer.spanBuilder(spanName).startSpan()
        val context = span.spanContext
        MDC.put(LoggingContextConstants.TRACE_ID, context.traceId)
        MDC.put(LoggingContextConstants.SPAN_ID, context.spanId)
        return try {
            block()
        } finally {
            MDC.remove(LoggingContextConstants.TRACE_ID)
            MDC.remove(LoggingContextConstants.SPAN_ID)
            span.end()
        }
    }
}
