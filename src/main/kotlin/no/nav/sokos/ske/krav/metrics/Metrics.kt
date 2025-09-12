package no.nav.sokos.ske.krav.metrics

import java.util.concurrent.ConcurrentHashMap

import io.ktor.http.isSuccess
import io.micrometer.core.instrument.Counter
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry

import no.nav.sokos.ske.krav.domain.ENDRING_HOVEDSTOL
import no.nav.sokos.ske.krav.domain.ENDRING_RENTE
import no.nav.sokos.ske.krav.domain.NYTT_KRAV
import no.nav.sokos.ske.krav.domain.STOPP_KRAV
import no.nav.sokos.ske.krav.util.RequestResult

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

    fun incrementMetrics(results: List<RequestResult>) {
        numberOfKravSent.increment(results.size.toDouble())
        numberOfKravFeilet.increment(results.filter { !it.response.status.isSuccess() }.size.toDouble())
        numberOfNyeKrav.increment(results.filter { it.krav.kravtype == NYTT_KRAV }.size.toDouble())
        numberOfEndringerAvKrav.increment(results.filter { it.krav.kravtype == ENDRING_RENTE || it.krav.kravtype == ENDRING_HOVEDSTOL }.size.toDouble())
        numberOfStoppAvKrav.increment(results.filter { it.krav.kravtype == STOPP_KRAV }.size.toDouble())
    }

    private fun counter(
        name: String,
        description: String,
    ) = Counter
        .builder("${NAMESPACE}_$name ")
        .description(description)
        .register(registry)
}
