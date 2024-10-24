package sokos.ske.krav.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import java.util.concurrent.ConcurrentHashMap

object Metrics {
    val registry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    private const val NAMESPACE = "sokos_ske_krav"

    val numberOfKravRead: Counter = counter("krav_lest_test", "Antall krav lest fra fil")
    val numberOfKravSent: Counter = counter("krav_sendt_test", "Antall krav sendt til endepunkt")
    val numberOfNyeKrav: Counter = counter("nye_krav_sendt_test", "Antall nye krav sendt")
    val numberOfEndringerAvKrav: Counter = counter("endringer_krav_sendt_test", "Antall endringer av krav sendt")
    val numberOfStoppAvKrav: Counter = counter("stopp_krav_sendt_test", "Antall stopp av krav sendt")
    val numberOfKravFeilet: Counter = counter("krav_feilet_test", "Antall krav som har feilet")
    val numberOfKravResent: Counter = counter("krav_resendt_test", "Antall krav som er resendt")

    private val kodeKravSendtCounters = ConcurrentHashMap<String, Counter>()

    fun incrementKravKodeSendtMetric(kravkode: String) {
        kodeKravSendtCounters
            .computeIfAbsent(kravkode) {
                Counter
                    .builder("${NAMESPACE}_kode_krav_sendt")
                    .description("type krav sendt til endepunkt")
                    .tag("kravkode", kravkode)
                    .register(registry)
            }.increment()
    }

    private fun counter(
        name: String,
        description: String,
    ) = Counter
        .builder("${NAMESPACE}_$name ")
        .description(description)
        .register(registry)
}
