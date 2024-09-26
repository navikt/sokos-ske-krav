package sokos.ske.krav.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry

object Metrics {
    val registry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    private const val NAMESPACE = "sokos_ske_krav"

    val numberOfKravRead: Counter =
        Counter
            .builder("${NAMESPACE}_krav_lest")
            .description("antall krav Lest fra fil")
            .register(registry)

    val numberOfKravSent: Counter =
        Counter
            .builder("${NAMESPACE}_krav_sendt")
            .description("antall krav sendt til endepunkt")
            .register(registry)

    val numberOfKravResent: Counter =
        Counter
            .builder("${NAMESPACE}_krav_resendt")
            .description("antall krav resendt")
            .register(registry)

    fun registerTypeKravSendtMetric(kravType: String): Counter =
        Counter
            .builder("${NAMESPACE}_type_krav_sendt")
            .description("type krav sendt til endepunkt")
            .tag("kravtype", kravType)
            .register(registry)

    fun registerFileValidationError(
        fileName: String,
        message: String,
    ): Counter =
        Counter
            .builder("${NAMESPACE}_filvalidering_feil")
            .tag("fileName", fileName)
            .tag("message", message)
            .register(registry)

    fun registerLineValidationError(
        fileName: String,
        message: String,
    ): Counter =
        Counter
            .builder("${NAMESPACE}_linjevalidering_feil")
            .tag("fileName", fileName)
            .tag("message", message)
            .register(registry)

    fun registerRequestError(
        fileName: String,
        message: String,
    ): Counter =
        Counter
            .builder("${NAMESPACE}_innsending_av_krav_feil")
            .tag("fileName", fileName)
            .tag("message", message)
            .register(registry)
}
