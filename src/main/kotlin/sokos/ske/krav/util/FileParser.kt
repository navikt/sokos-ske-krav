package sokos.ske.krav.util

import mu.KotlinLogging
import sokos.ske.krav.domain.nav.KontrollLinjeHeader
import sokos.ske.krav.domain.nav.KravLinje
import sokos.ske.krav.domain.nav.KontrollLinjeFooter
import sokos.ske.krav.util.FileParser.KravLinjeFeltPosisjoner.ARSAK_KODE_POS
import sokos.ske.krav.util.FileParser.KravLinjeFeltPosisjoner.BELOP_POS
import sokos.ske.krav.util.FileParser.KravLinjeFeltPosisjoner.BELOP_RENTE_POS
import sokos.ske.krav.util.FileParser.KravLinjeFeltPosisjoner.ENHET_BEHANDLENDE_POS
import sokos.ske.krav.util.FileParser.KravLinjeFeltPosisjoner.ENHET_BOSTED_POS
import sokos.ske.krav.util.FileParser.KravLinjeFeltPosisjoner.FAGSYSTEM_ID_POS
import sokos.ske.krav.util.FileParser.KravLinjeFeltPosisjoner.FREMTIDIG_YTELSE_POS
import sokos.ske.krav.util.FileParser.KravLinjeFeltPosisjoner.GJELDER_ID_POS
import sokos.ske.krav.util.FileParser.KravLinjeFeltPosisjoner.HJEMMEL_KODE_POS
import sokos.ske.krav.util.FileParser.KravLinjeFeltPosisjoner.LINJE_NUMMER_POS
import sokos.ske.krav.util.FileParser.KravLinjeFeltPosisjoner.PERIODE_FOM_POS
import sokos.ske.krav.util.FileParser.KravLinjeFeltPosisjoner.PERIODE_TOM_POS
import sokos.ske.krav.util.FileParser.KravLinjeFeltPosisjoner.REFERANSE_GAMMEL_SAK_POS
import sokos.ske.krav.util.FileParser.KravLinjeFeltPosisjoner.SAKS_NUMMER_POS
import sokos.ske.krav.util.FileParser.KravLinjeFeltPosisjoner.STONADS_KODE_POS
import sokos.ske.krav.util.FileParser.KravLinjeFeltPosisjoner.TRANSAKSJONS_DATO_POS
import sokos.ske.krav.util.FileParser.KravLinjeFeltPosisjoner.UTBETAL_DATO_POS
import sokos.ske.krav.util.FileParser.KravLinjeFeltPosisjoner.VEDTAK_DATO_POS
import sokos.ske.krav.util.FileParser.SisteLinjeFeltPosisjoner.ANTALL_LINJER_POS
import sokos.ske.krav.util.FileParser.SisteLinjeFeltPosisjoner.OVERFORINGS_DATO_POS
import sokos.ske.krav.util.FileParser.SisteLinjeFeltPosisjoner.SENDER_POS
import sokos.ske.krav.util.FileParser.SisteLinjeFeltPosisjoner.SUM_ALLE_LINJER_POS
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class FileParser(val content: List<String>) {

    fun parseKontrollLinjeHeader() = kontrollLinjeHeaderParser(content.first())
    fun parseKontrollLinjeFooter() = kontrollLinjeFooterParser(content.last())
    fun parseKravLinjer() = content.subList(1, content.lastIndex).map { kravLinjeParser(it) }

    private fun kontrollLinjeHeaderParser(linje: String): KontrollLinjeHeader =
        KontrollLinjeHeader(
            transaksjonDato = OVERFORINGS_DATO_POS.parseString(linje),
            avsender = SENDER_POS.parseString(linje),
        )

    private fun kontrollLinjeFooterParser(linje: String): KontrollLinjeFooter =
        KontrollLinjeFooter(
            transaksjonDato = OVERFORINGS_DATO_POS.parseString(linje),
            avsender = SENDER_POS.parseString(linje),
            antallTransaksjoner = ANTALL_LINJER_POS.parseInt(linje),
            sumAlleTransaksjoner = SUM_ALLE_LINJER_POS.parseBigDecimal(linje),
        )

    private fun kravLinjeParser(linje: String) = KravLinje(
        linjeNummer = LINJE_NUMMER_POS.parseInt(linje),
        saksNummer = SAKS_NUMMER_POS.parseString(linje),
        BELOP_POS.parseBigDecimal(linje),
        VEDTAK_DATO_POS.parseDate(linje),
        GJELDER_ID_POS.parseString(linje),
        PERIODE_FOM_POS.parseString(linje),
        PERIODE_TOM_POS.parseString(linje),
        STONADS_KODE_POS.parseString(linje),
        REFERANSE_GAMMEL_SAK_POS.parseString(linje),
        TRANSAKSJONS_DATO_POS.parseString(linje),
        ENHET_BOSTED_POS.parseString(linje),
        ENHET_BEHANDLENDE_POS.parseString(linje),
        HJEMMEL_KODE_POS.parseString(linje),
        ARSAK_KODE_POS.parseString(linje),
        BELOP_RENTE_POS.parseBigDecimal(linje),
        FREMTIDIG_YTELSE_POS.parseBigDecimal(linje),
        UTBETAL_DATO_POS.parseString(linje),
        FAGSYSTEM_ID_POS.parseString(linje),
    )

    private object SisteLinjeFeltPosisjoner {
        val OVERFORINGS_DATO_POS = Posisjon(start = 4, end = 18)
        val SENDER_POS = Posisjon(start = 18, end = 27)
        val ANTALL_LINJER_POS = Posisjon(start = 27, end = 35)
        val SUM_ALLE_LINJER_POS = Posisjon(start = 35, end = 50)
    }

    private object KravLinjeFeltPosisjoner {
        val LINJE_TYPE_POS = Posisjon(start = 0, end = 4)
        val LINJE_NUMMER_POS = Posisjon(start = 4, end = 11)
        val SAKS_NUMMER_POS = Posisjon(start = 11, end = 29)
        val BELOP_POS = Posisjon(start = 29, end = 40)
        val VEDTAK_DATO_POS = Posisjon(start = 40, end = 48)
        val GJELDER_ID_POS = Posisjon(start = 48, end = 59)
        val PERIODE_FOM_POS = Posisjon(start = 59, end = 67)
        val PERIODE_TOM_POS = Posisjon(start = 67, end = 75)
        val STONADS_KODE_POS = Posisjon(start = 75, end = 83)
        val REFERANSE_GAMMEL_SAK_POS = Posisjon(start = 83, end = 101)
        val TRANSAKSJONS_DATO_POS = Posisjon(start = 101, end = 109)
        val ENHET_BOSTED_POS = Posisjon(start = 109, end = 113)
        val ENHET_BEHANDLENDE_POS = Posisjon(start = 113, end = 117)
        val HJEMMEL_KODE_POS = Posisjon(start = 117, end = 119)
        val ARSAK_KODE_POS = Posisjon(start = 119, end = 131)
        val BELOP_RENTE_POS = Posisjon(start = 131, end = 151)
        val FREMTIDIG_YTELSE_POS = Posisjon(start = 151, end = 162)
        val UTBETAL_DATO_POS = Posisjon(start = 162, end = 170)
        val FAGSYSTEM_ID_POS = Posisjon(start = 170, end = 200)
    }

    private data class Posisjon(val start: Int, val end: Int) {
        private val logger = KotlinLogging.logger {}

        fun parseString(line: String): String {
            if (start > end) {
                logger.error("Feil i fil! Startposisjon $start er større enn sluttposisjon $end")
                return ""
            }
            return if (start > line.length) ""
            else if (end > line.length) line.substring(start).trim()
            else line.substring(start, end).trim()
        }

        fun parseBigDecimal(line: String): BigDecimal {
            val amount = parseString(line)
            val integer = amount.dropLast(2)
            val dec = amount.drop(amount.length - 2)

            return "$integer.$dec".toBigDecimal()
        }

        fun parseInt(line: String): Int = parseString(line).toInt()
        fun parseDate(line: String): LocalDate {
            val dateString = parseString(line)
            val dtf = DateTimeFormatter.ofPattern("yyyyMMdd")
            return LocalDate.parse(dateString, dtf)
        }
    }
}
