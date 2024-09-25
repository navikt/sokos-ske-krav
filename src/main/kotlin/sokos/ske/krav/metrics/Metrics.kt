package sokos.ske.krav.metrics

import io.prometheus.metrics.core.metrics.Counter
import io.prometheus.metrics.model.registry.PrometheusRegistry

object Metrics {
    //  val registry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    private const val NAMESPACE = "sokos_ske_krav"
    val registry = PrometheusRegistry()

    val numberOfKravRead: Counter =
        Counter
            .builder() // kan sende inn config
            .name("krav_lest")
            .help("antall krav Lest fra fil")
            .register(registry)

    val numberOfKravSent: Counter =
        Counter
            .builder()
            .name("krav_sendt")
            .help("antall krav sendt til endepunkt")
            .register(registry)

    val numberOfKravResent: Counter =
        Counter
            .builder()
            .name("krav_resendt")
            .help("antall krav resendt")
            .register(registry)

    val typeKravSent: Counter =
        Counter
            .builder()
            .name("type_krav_sendt")
            .help("type krav sendt til endepunkt")
            .labelNames("kravtype")
            .register(registry)

    val fileValidationError: Counter =
        Counter
            .builder()
            .name("filvalidering_feil")
            .help("feil i validering av fil")
            .labelNames("fileName", "message")
            .register(registry)

    val lineValidationError: Counter =
        Counter
            .builder()
            .name("linjevalidering_feil")
            .help("feil i validering av linje")
            .labelNames("fileName", "message")
            .register(registry)

    val requestError: Counter =
        Counter
            .builder()
            .name("innsending_av_krav_feil")
            .help("feil i innsending av krav")
            .labelNames("fileName", "message")
            .register(registry)
}
