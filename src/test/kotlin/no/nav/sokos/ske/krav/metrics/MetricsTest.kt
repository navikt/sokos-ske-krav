package no.nav.sokos.ske.krav.metrics

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.shouldBeExactly
import io.kotest.matchers.shouldBe

class MetricsTest :
    FunSpec({

        test("registerKravKodeCounter should register counter without incrementing") {
            val kravkode = "TEST_REG"

            Metrics.registerKravKodeCounter(kravkode)

            val counter =
                Metrics.registry
                    .find("sokos_ske_krav_kode_krav_sendt")
                    .tag("kravkode", kravkode)
                    .counter()

            counter?.count()?.shouldBeExactly(0.0)
        }

        test("incrementKravKodeSendtMetric should increment counter") {
            val kravkode = "TEST_INC"

            Metrics.incrementKravKodeSendtMetric(kravkode)
            Metrics.incrementKravKodeSendtMetric(kravkode)

            val counter =
                Metrics.registry
                    .find("sokos_ske_krav_kode_krav_sendt")
                    .tag("kravkode", kravkode)
                    .counter()

            counter?.count()?.shouldBeExactly(2.0)
        }

        test("getCounter should return same instance for same kravkode") {
            val kravkode = "TEST_SAME"

            Metrics.incrementKravKodeSendtMetric(kravkode)
            val count1 =
                Metrics.registry
                    .find("sokos_ske_krav_kode_krav_sendt")
                    .tag("kravkode", kravkode)
                    .counter()
                    ?.count()

            Metrics.incrementKravKodeSendtMetric(kravkode)
            val count2 =
                Metrics.registry
                    .find("sokos_ske_krav_kode_krav_sendt")
                    .tag("kravkode", kravkode)
                    .counter()
                    ?.count()

            count2 shouldBe (count1?.plus(1.0))
        }
    })
