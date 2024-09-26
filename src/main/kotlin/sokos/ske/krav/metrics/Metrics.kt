package sokos.ske.krav.metrics

import io.prometheus.metrics.core.metrics.Counter
import io.prometheus.metrics.model.registry.PrometheusRegistry

object Metrics {
    //  val registry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    private const val NAMESPACE = "sokos_ske_krav"
    private val registry = PrometheusRegistry()

    val numberOfKravRead: Counter =
        Counter
            .builder() // kan sende inn config
            .name("sokos_ske_krav_krav_lest")
            .help("antall krav Lest fra fil")
            .register()

    val numberOfKravSent: Counter =
        Counter
            .builder()
            .name("sokos_ske_krav_krav_sendt")
            .help("antall krav sendt til endepunkt")
            .register()

    val numberOfKravResent: Counter =
        Counter
            .builder()
            .name("sokos_ske_krav_krav_resendt")
            .help("antall krav resendt")
            .register()

    val typeKravSent: Counter =
        Counter
            .builder()
            .name("sokos_ske_krav_type_krav_sendt")
            .help("type krav sendt til endepunkt")
            .labelNames("kravtype")
            .register()

    val fileValidationError: Counter =
        Counter
            .builder()
            .name("sokos_ske_krav_filvalidering_feil")
            .help("feil i validering av fil")
            .labelNames("fileName", "message")
            .register()

    val lineValidationError: Counter =
        Counter
            .builder()
            .name("sokos_ske_krav_linjevalidering_feil")
            .help("feil i validering av linje")
            .labelNames("fileName", "message")
            .register()

    val requestError: Counter =
        Counter
            .builder()
            .name("sokos_ske_krav_innsending_av_krav_feil")
            .help("feil i innsending av krav")
            .labelNames("fileName", "message")
            .register()
}
