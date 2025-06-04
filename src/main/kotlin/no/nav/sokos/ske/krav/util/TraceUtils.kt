package no.nav.sokos.ske.krav.util

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import io.opentelemetry.instrumentation.api.incubator.log.LoggingContextConstants
import org.slf4j.MDC

object TraceUtils {
    private val openTelemetry = GlobalOpenTelemetry.get()

    suspend fun <T> withTracerId(
        tracer: Tracer = openTelemetry.getTracer(this::class.java.canonicalName),
        spanName: String = "withTracerId",
        forceNewTrace: Boolean = false,
        block: suspend () -> T,
    ): T {
        val spanBuilder = tracer.spanBuilder(spanName)

        // Create a new root span with no parent context when forcing a new trace
        if (forceNewTrace) {
            spanBuilder.setNoParent()
        }

        val span = spanBuilder.startSpan()
        val context = span.spanContext

        // Make the span the current active span in the context
        return Context.current().with(span).makeCurrent().use { scope ->
            try {
                MDC.put(LoggingContextConstants.TRACE_ID, context.traceId)
                MDC.put(LoggingContextConstants.SPAN_ID, context.spanId)

                val result = block()
                span.setStatus(StatusCode.OK)
                result
            } catch (e: Exception) {
                span.setStatus(StatusCode.ERROR, e.message ?: "Unknown error")
                span.recordException(e)
                throw e
            } finally {
                MDC.remove(LoggingContextConstants.TRACE_ID)
                MDC.remove(LoggingContextConstants.SPAN_ID)
                span.end()
            }
        }
    }
}
