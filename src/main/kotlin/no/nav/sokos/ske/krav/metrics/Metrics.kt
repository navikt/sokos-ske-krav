package no.nav.sokos.ske.krav.metrics

import java.util.concurrent.ConcurrentHashMap

import io.micrometer.core.instrument.Counter
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry

object Metrics {
    val registry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    private const val NAMESPACE = "sokos_ske_krav"

    val numberOfKravRead: Counter by lazy { counter("krav_innlest_lest", "Antall krav lest fra fil") }
    val numberOfKravSent: Counter by lazy { counter("krav_sendt_til_ske", "Antall krav sendt til endepunkt") }
    val numberOfNyeKrav: Counter by lazy { counter("nye_krav_sendt", "Antall nye krav sendt") }
    val numberOfEndringerAvKrav: Counter by lazy { counter("endringer_krav_sendt", "Antall endringer av krav sendt") }
    val numberOfStoppAvKrav: Counter by lazy { counter("stopp_krav_sendt", "Antall stopp av krav sendt") }
    val numberOfKravFeilet: Counter by lazy { counter("krav_feilet_i_innsending", "Antall krav som har feilet") }
    val numberOfKravResent: Counter by lazy { counter("krav_resendt_til_ske", "Antall krav som er resendt") }

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
