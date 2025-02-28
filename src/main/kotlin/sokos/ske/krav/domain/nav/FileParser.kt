package sokos.ske.krav.domain.nav

import sokos.ske.krav.domain.nav.FileParser.KravLinjeFeltPosisjoner.ARSAK_KODE_POS
import sokos.ske.krav.domain.nav.FileParser.KravLinjeFeltPosisjoner.BELOP_POS
import sokos.ske.krav.domain.nav.FileParser.KravLinjeFeltPosisjoner.BELOP_RENTE_POS
import sokos.ske.krav.domain.nav.FileParser.KravLinjeFeltPosisjoner.ENHET_BEHANDLENDE_POS
import sokos.ske.krav.domain.nav.FileParser.KravLinjeFeltPosisjoner.ENHET_BOSTED_POS
import sokos.ske.krav.domain.nav.FileParser.KravLinjeFeltPosisjoner.FAGSYSTEM_ID_POS
import sokos.ske.krav.domain.nav.FileParser.KravLinjeFeltPosisjoner.FREMTIDIG_YTELSE_POS
import sokos.ske.krav.domain.nav.FileParser.KravLinjeFeltPosisjoner.GJELDER_ID_POS
import sokos.ske.krav.domain.nav.FileParser.KravLinjeFeltPosisjoner.HJEMMEL_KODE_POS
import sokos.ske.krav.domain.nav.FileParser.KravLinjeFeltPosisjoner.KRAV_KODE_POS
import sokos.ske.krav.domain.nav.FileParser.KravLinjeFeltPosisjoner.LINJE_NUMMER_POS
import sokos.ske.krav.domain.nav.FileParser.KravLinjeFeltPosisjoner.PERIODE_FOM_POS
import sokos.ske.krav.domain.nav.FileParser.KravLinjeFeltPosisjoner.PERIODE_TOM_POS
import sokos.ske.krav.domain.nav.FileParser.KravLinjeFeltPosisjoner.REFERANSE_GAMMEL_SAK_POS
import sokos.ske.krav.domain.nav.FileParser.KravLinjeFeltPosisjoner.SAKS_NUMMER_POS
import sokos.ske.krav.domain.nav.FileParser.KravLinjeFeltPosisjoner.TRANSAKSJONS_DATO_POS
import sokos.ske.krav.domain.nav.FileParser.KravLinjeFeltPosisjoner.UTBETAL_DATO_POS
import sokos.ske.krav.domain.nav.FileParser.KravLinjeFeltPosisjoner.VEDTAK_DATO_POS
import sokos.ske.krav.domain.nav.FileParser.SisteLinjeFeltPosisjoner.ANTALL_LINJER_POS
import sokos.ske.krav.domain.nav.FileParser.SisteLinjeFeltPosisjoner.OVERFORINGS_DATO_POS
import sokos.ske.krav.domain.nav.FileParser.SisteLinjeFeltPosisjoner.SENDER_POS
import sokos.ske.krav.domain.nav.FileParser.SisteLinjeFeltPosisjoner.SUM_ALLE_LINJER_POS
import sokos.ske.krav.validation.LineValidationRules
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class ParseException(
    message: String,
) : Exception(message)

class FileParser(
    private val content: List<String>,
) {
    fun parseKontrollLinjeHeader() = kontrollLinjeHeaderParser(content.first())

    fun parseKontrollLinjeFooter() = kontrollLinjeFooterParser(content.last())

    fun parseKravLinjer() = content.subList(1, content.lastIndex).map { kravLinjeParser(it) }

    private fun kontrollLinjeHeaderParser(linje: String): KontrollLinjeHeader =
        KontrollLinjeHeader(
            transaksjonsDato = OVERFORINGS_DATO_POS.parseString(linje),
            avsender = SENDER_POS.parseString(linje),
        )

    private fun kontrollLinjeFooterParser(linje: String): KontrollLinjeFooter =
        KontrollLinjeFooter(
            transaksjonTimestamp = OVERFORINGS_DATO_POS.parseString(linje),
            avsender = SENDER_POS.parseString(linje),
            antallTransaksjoner = ANTALL_LINJER_POS.parseInt(linje),
            sumAlleTransaksjoner = SUM_ALLE_LINJER_POS.parseBigDecimal(linje),
        )

    private fun kravLinjeParser(linje: String) =
        KravLinje(
            linjenummer = LINJE_NUMMER_POS.parseInt(linje),
            saksnummerNav = SAKS_NUMMER_POS.parseString(linje),
            belop = BELOP_POS.parseBigDecimal(linje),
            vedtaksDato = VEDTAK_DATO_POS.parseDate(linje),
            gjelderId = GJELDER_ID_POS.parseString(linje),
            periodeFOM = PERIODE_FOM_POS.parseString(linje),
            periodeTOM = PERIODE_TOM_POS.parseString(linje),
            kravKode = KRAV_KODE_POS.parseString(linje).replace(0xFFFD.toChar(), 'Ø'),
            referansenummerGammelSak = REFERANSE_GAMMEL_SAK_POS.parseString(linje),
            transaksjonsDato = TRANSAKSJONS_DATO_POS.parseString(linje),
            enhetBosted = ENHET_BOSTED_POS.parseString(linje),
            enhetBehandlende = ENHET_BEHANDLENDE_POS.parseString(linje),
            kodeHjemmel = HJEMMEL_KODE_POS.parseString(linje),
            kodeArsak = ARSAK_KODE_POS.parseString(linje),
            belopRente = BELOP_RENTE_POS.parseBigDecimal(linje),
            fremtidigYtelse = FREMTIDIG_YTELSE_POS.parseBigDecimal(linje),
            utbetalDato = UTBETAL_DATO_POS.parseDate(linje),
            fagsystemId = FAGSYSTEM_ID_POS.parseString(linje),
        )

    private data class Field(
        val start: Int,
        val end: Int,
    ) {
        fun parseString(line: String): String =
            runCatching {
                line.substring(start.coerceAtMost(line.length), end.coerceAtMost(line.length)).trim()
            }.getOrElse { throw ParseException("Feil i parsing av kravlinje: Startposisjon $start er større enn sluttposisjon $end") }

        fun parseBigDecimal(line: String): BigDecimal {
            val amount = parseString(line)
            return if (amount.length < 3) {
                BigDecimal.ZERO
            } else {
                runCatching {
                    BigDecimal("${amount.dropLast(2)}.${amount.takeLast(2)}")
                }.getOrElse { throw ParseException("Feil i parsing av BigDecimal ($start, $end): ${it.message}") }
            }
        }

        fun parseInt(line: String): Int =
            runCatching {
                parseString(line).toInt()
            }.getOrElse {
                throw ParseException("Feil i parsing av Int ($start, $end): ${it.message}")
            }

        fun parseDate(line: String): LocalDate =
            runCatching {
                LocalDate.parse(parseString(line), DateTimeFormatter.ofPattern("yyyyMMdd"))
            }.getOrDefault(LineValidationRules.errorDate)
    }

    private object SisteLinjeFeltPosisjoner {
        val OVERFORINGS_DATO_POS = Field(start = 4, end = 18)
        val SENDER_POS = Field(start = 18, end = 27)
        val ANTALL_LINJER_POS = Field(start = 27, end = 35)
        val SUM_ALLE_LINJER_POS = Field(start = 35, end = 50)
    }

    private object KravLinjeFeltPosisjoner {
        val LINJE_NUMMER_POS = Field(start = 4, end = 11)
        val SAKS_NUMMER_POS = Field(start = 11, end = 29)
        val BELOP_POS = Field(start = 29, end = 40)
        val VEDTAK_DATO_POS = Field(start = 40, end = 48)
        val GJELDER_ID_POS = Field(start = 48, end = 59)
        val PERIODE_FOM_POS = Field(start = 59, end = 67)
        val PERIODE_TOM_POS = Field(start = 67, end = 75)
        val KRAV_KODE_POS = Field(start = 75, end = 83)
        val REFERANSE_GAMMEL_SAK_POS = Field(start = 83, end = 101)
        val TRANSAKSJONS_DATO_POS = Field(start = 101, end = 109)
        val ENHET_BOSTED_POS = Field(start = 109, end = 113)
        val ENHET_BEHANDLENDE_POS = Field(start = 113, end = 117)
        val HJEMMEL_KODE_POS = Field(start = 117, end = 119)
        val ARSAK_KODE_POS = Field(start = 119, end = 131)
        val BELOP_RENTE_POS = Field(start = 131, end = 151)
        val FREMTIDIG_YTELSE_POS = Field(start = 151, end = 162)
        val UTBETAL_DATO_POS = Field(start = 162, end = 170)
        val FAGSYSTEM_ID_POS = Field(start = 170, end = 200)
    }
}
