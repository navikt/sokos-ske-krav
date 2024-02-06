package sokos.ske.krav.util

import mu.KotlinLogging
import sokos.ske.krav.domain.nav.FirstLine
import sokos.ske.krav.domain.nav.KravLinje
import sokos.ske.krav.domain.nav.LastLine
import sokos.ske.krav.util.FilParser.KravLinjeFeltPosisjoner.ARSAK_KODE_POS
import sokos.ske.krav.util.FilParser.KravLinjeFeltPosisjoner.BELOP_POS
import sokos.ske.krav.util.FilParser.KravLinjeFeltPosisjoner.BELOP_RENTE_POS
import sokos.ske.krav.util.FilParser.KravLinjeFeltPosisjoner.ENHET_BEHANDLENDE_POS
import sokos.ske.krav.util.FilParser.KravLinjeFeltPosisjoner.ENHET_BOSTED_POS
import sokos.ske.krav.util.FilParser.KravLinjeFeltPosisjoner.FAGSYSTEM_ID_POS
import sokos.ske.krav.util.FilParser.KravLinjeFeltPosisjoner.FREMTIDIG_YTELSE_POS
import sokos.ske.krav.util.FilParser.KravLinjeFeltPosisjoner.GJELDER_ID_POS
import sokos.ske.krav.util.FilParser.KravLinjeFeltPosisjoner.HJEMMEL_KODE_POS
import sokos.ske.krav.util.FilParser.KravLinjeFeltPosisjoner.LINJE_NUMMER_POS
import sokos.ske.krav.util.FilParser.KravLinjeFeltPosisjoner.PERIODE_FOM_POS
import sokos.ske.krav.util.FilParser.KravLinjeFeltPosisjoner.PERIODE_TOM_POS
import sokos.ske.krav.util.FilParser.KravLinjeFeltPosisjoner.REFERANSE_GAMMEL_SAK_POS
import sokos.ske.krav.util.FilParser.KravLinjeFeltPosisjoner.SAKS_NUMMER_POS
import sokos.ske.krav.util.FilParser.KravLinjeFeltPosisjoner.STONADS_KODE_POS
import sokos.ske.krav.util.FilParser.KravLinjeFeltPosisjoner.TRANSAKSJONS_DATO_POS
import sokos.ske.krav.util.FilParser.KravLinjeFeltPosisjoner.UTBETAL_DATO_POS
import sokos.ske.krav.util.FilParser.KravLinjeFeltPosisjoner.VEDTAK_DATO_POS
import sokos.ske.krav.util.FilParser.SisteLinjeFeltPosisjoner.ANTALL_LINJER_POS
import sokos.ske.krav.util.FilParser.SisteLinjeFeltPosisjoner.OVERFORINGS_DATO_POS
import sokos.ske.krav.util.FilParser.SisteLinjeFeltPosisjoner.SENDER_POS
import sokos.ske.krav.util.FilParser.SisteLinjeFeltPosisjoner.SUM_ALLE_LINJER_POS
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class FilParser(val content: List<String>) {

    fun parseForsteLinje() = forsteLinjeParser(content.first())
    fun parseSisteLinje() = sisteLinjeParser(content.last())
    fun parseKravLinjer() = content.subList(1, content.lastIndex).map { kravLinjeParser(it) }

    private fun forsteLinjeParser(linje: String): FirstLine =
        FirstLine(
            transferDate = OVERFORINGS_DATO_POS.parseString(linje),
            sender = SENDER_POS.parseString(linje),
        )

    private fun sisteLinjeParser(linje: String): LastLine =
        LastLine(
            transferDate = OVERFORINGS_DATO_POS.parseString(linje),
            sender = SENDER_POS.parseString(linje),
            numTransactionLines = ANTALL_LINJER_POS.parseInt(linje),
            sumAllTransactionLines = SUM_ALLE_LINJER_POS.parseBigDecimal(linje),
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
        val OVERFORINGS_DATO_POS = LinjeFeltPosisjon(start = 4, end = 18)
        val SENDER_POS = LinjeFeltPosisjon(start = 18, end = 27)
        val ANTALL_LINJER_POS = LinjeFeltPosisjon(start = 27, end = 35)
        val SUM_ALLE_LINJER_POS = LinjeFeltPosisjon(start = 35, end = 50)
    }

    private object KravLinjeFeltPosisjoner {
        val LINJE_TYPE_POS = LinjeFeltPosisjon(start = 0, end = 4)
        val LINJE_NUMMER_POS = LinjeFeltPosisjon(start = 4, end = 11)
        val SAKS_NUMMER_POS = LinjeFeltPosisjon(start = 11, end = 29)
        val BELOP_POS = LinjeFeltPosisjon(start = 29, end = 40)
        val VEDTAK_DATO_POS = LinjeFeltPosisjon(start = 40, end = 48)
        val GJELDER_ID_POS = LinjeFeltPosisjon(start = 48, end = 59)
        val PERIODE_FOM_POS = LinjeFeltPosisjon(start = 59, end = 67)
        val PERIODE_TOM_POS = LinjeFeltPosisjon(start = 67, end = 75)
        val STONADS_KODE_POS = LinjeFeltPosisjon(start = 75, end = 83)
        val REFERANSE_GAMMEL_SAK_POS = LinjeFeltPosisjon(start = 83, end = 101)
        val TRANSAKSJONS_DATO_POS = LinjeFeltPosisjon(start = 101, end = 109)
        val ENHET_BOSTED_POS = LinjeFeltPosisjon(start = 109, end = 113)
        val ENHET_BEHANDLENDE_POS = LinjeFeltPosisjon(start = 113, end = 117)
        val HJEMMEL_KODE_POS = LinjeFeltPosisjon(start = 117, end = 119)
        val ARSAK_KODE_POS = LinjeFeltPosisjon(start = 119, end = 131)
        val BELOP_RENTE_POS = LinjeFeltPosisjon(start = 131, end = 151)
        val FREMTIDIG_YTELSE_POS = LinjeFeltPosisjon(start = 151, end = 162)
        val UTBETAL_DATO_POS = LinjeFeltPosisjon(start = 162, end = 170)
        val FAGSYSTEM_ID_POS = LinjeFeltPosisjon(start = 170, end = 200)
    }

    private data class LinjeFeltPosisjon(val start: Int, val end: Int) {
        private val logger = KotlinLogging.logger {}
        fun parseString(line: String): String {
            if (start > end) {
                logger.error { "Feil i fil! Startposisjon $start er større enn sluttposisjon $end" }
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
