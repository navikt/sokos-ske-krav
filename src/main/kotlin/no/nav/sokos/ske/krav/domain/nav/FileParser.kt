package no.nav.sokos.ske.krav.domain.nav

import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter

import io.ktor.http.parsing.ParseException

import no.nav.sokos.ske.krav.validation.LineValidationRules

class FileParser(
    private val content: List<String>,
) {
    fun parseKontrollLinjeHeader() = kontrollLinjeHeaderParser(content.first())

    fun parseKontrollLinjeFooter() = kontrollLinjeFooterParser(content.last())

    fun parseKravLinjer() = content.subList(1, content.lastIndex).map { kravLinjeParser(it) }

    private fun kontrollLinjeHeaderParser(linje: String): KontrollLinjeHeader =
        KontrollLinjeHeader(
            transaksjonsDato = linje.getString(start = 4, end = 18),
            avsender = linje.getString(start = 18, end = 27),
        )

    private fun kontrollLinjeFooterParser(linje: String): KontrollLinjeFooter =
        KontrollLinjeFooter(
            transaksjonTimestamp = linje.getString(start = 4, end = 18),
            avsender = linje.getString(start = 18, end = 27),
            antallTransaksjoner = linje.getInt(start = 27, end = 35),
            sumAlleTransaksjoner = linje.getBigDecimal(start = 35, end = 50),
        )

    private fun kravLinjeParser(linje: String) =
        with(linje) {
            KravLinje(
                linjenummer = getInt(start = 4, end = 11),
                saksnummerNav = getString(start = 11, end = 29),
                belop = getBigDecimal(start = 29, end = 40),
                vedtaksDato = getDate(start = 40, end = 48),
                gjelderId = getString(start = 48, end = 59),
                periodeFOM = getString(start = 59, end = 67),
                periodeTOM = getString(start = 67, end = 75),
                kravKode = getString(start = 75, end = 83).replace(0xFFFD.toChar(), 'Ø'),
                referansenummerGammelSak = getString(start = 83, end = 101),
                transaksjonsDato = getString(start = 101, end = 109),
                enhetBosted = getString(start = 109, end = 113),
                enhetBehandlende = getString(start = 113, end = 117),
                kodeHjemmel = getString(start = 117, end = 119),
                kodeArsak = getString(start = 119, end = 131),
                belopRente = getBigDecimal(start = 131, end = 151),
                fremtidigYtelse = getBigDecimal(start = 151, end = 162),
                utbetalDato = getDate(start = 162, end = 170),
                fagsystemId = getString(start = 170, end = 200),
                tilleggsfrist = getOptionalDate(start = 200, end = 208),
            )
        }

    private fun String.getString(
        start: Int,
        end: Int,
    ): String =
        runCatching {
            substring(start.coerceAtMost(length), end.coerceAtMost(length)).trim()
        }.getOrElse { throw ParseException("Feil i parsing av kravlinje: Startposisjon $start er større enn sluttposisjon $end") }

    private fun String.getBigDecimal(
        start: Int,
        end: Int,
    ): BigDecimal {
        val amount = getString(start, end)
        return if (amount.length < 3) {
            BigDecimal.ZERO
        } else {
            runCatching {
                BigDecimal("${amount.dropLast(2)}.${amount.takeLast(2)}")
            }.getOrElse { throw ParseException("Feil i parsing av BigDecimal ($start, $end): ${it.message}") }
        }
    }

    private fun String.getInt(
        start: Int,
        end: Int,
    ): Int =
        runCatching {
            getString(start, end).toInt()
        }.getOrElse {
            throw ParseException("Feil i parsing av Int ($start, $end): ${it.message}")
        }

    private fun String.getDate(
        start: Int,
        end: Int,
    ): LocalDate =
        runCatching {
            LocalDate.parse(getString(start, end), DateTimeFormatter.ofPattern("yyyyMMdd"))
        }.getOrDefault(LineValidationRules.errorDate)

    private fun String.getOptionalDate(
        start: Int,
        end: Int,
    ): LocalDate? =
        if (getString(start, end).isBlank()) {
            null
        } else {
            getDate(start, end)
        }
}
