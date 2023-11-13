package sokos.ske.krav.util

import mu.KotlinLogging
import sokos.ske.krav.domain.nav.FirstLine
import sokos.ske.krav.domain.nav.KravLinje
import sokos.ske.krav.domain.nav.LastLine
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class FilParser(val content: List<String>) {

    fun parseForsteLinje() = forsteLinjeParser(content.first())
    fun parseSisteLinje() = sisteLinjeParser(content.last())
    fun parseKravLinjer() = content.subList(1, content.lastIndex).map { kravLinjeParser(it) }

    private fun forsteLinjeParser(linje: String): FirstLine =
        FirstLine(
            ForsteLinjeFeltPosisjoner.overforingsDato.parseString(linje),
            ForsteLinjeFeltPosisjoner.sender.parseString(linje),
        )

    private fun sisteLinjeParser(linje: String): LastLine =
        LastLine(
            SisteLinjeFeltPosisjoner.overforingsDato.parseString(linje),
            SisteLinjeFeltPosisjoner.sender.parseString(linje),
            SisteLinjeFeltPosisjoner.antallLinjer.parseInt(linje),
            SisteLinjeFeltPosisjoner.sumAlleLinjer.parseDouble(linje),
        )

    private fun kravLinjeParser(linje: String) = KravLinje(
        KravLinjeFeltPosisjoner.linjeNummer.parseInt(linje),
        KravLinjeFeltPosisjoner.saksNummer.parseString(linje),
        KravLinjeFeltPosisjoner.belop.parseDouble(linje),
        KravLinjeFeltPosisjoner.vedtakDato.parseDate(linje),
        KravLinjeFeltPosisjoner.gjelderID.parseString(linje),
        KravLinjeFeltPosisjoner.periodeFom.parseString(linje),
        KravLinjeFeltPosisjoner.periodeTom.parseString(linje),
        KravLinjeFeltPosisjoner.stonadsKode.parseString(linje),
        KravLinjeFeltPosisjoner.referanseGammelSak.parseString(linje),
        KravLinjeFeltPosisjoner.transaksjonsDato.parseString(linje),
        KravLinjeFeltPosisjoner.enhetBosted.parseString(linje),
        KravLinjeFeltPosisjoner.enhetBehandlende.parseString(linje),
        KravLinjeFeltPosisjoner.hjemmelKode.parseString(linje),
        KravLinjeFeltPosisjoner.arsakKode.parseString(linje),
        KravLinjeFeltPosisjoner.belopRente.parseDouble(linje),
        KravLinjeFeltPosisjoner.fremtidigYtelse.parseDouble(linje),
        KravLinjeFeltPosisjoner.utbetalDato.parseString(linje),
        KravLinjeFeltPosisjoner.fagsystemID.parseString(linje),
    )

    private object SisteLinjeFeltPosisjoner {
        val overforingsDato = LinjeFeltPosisjon(start = 4, end = 18)
        val sender = LinjeFeltPosisjon(start = overforingsDato.end, end = 27)
        val antallLinjer = LinjeFeltPosisjon(start = sender.end, end = 35)
        val sumAlleLinjer = LinjeFeltPosisjon(start = antallLinjer.end, end = 50)
    }

    private object ForsteLinjeFeltPosisjoner {
        val overforingsDato = LinjeFeltPosisjon(start = 4, end = 18)
        val sender = LinjeFeltPosisjon(start = overforingsDato.end, end = 27)
    }

    private object KravLinjeFeltPosisjoner {
        val linjeType = LinjeFeltPosisjon(start = 0, end = 4)
        val linjeNummer = LinjeFeltPosisjon(start = linjeType.end, end = 11)
        val saksNummer = LinjeFeltPosisjon(start = linjeNummer.end, end = 29)
        val belop = LinjeFeltPosisjon(start = saksNummer.end, end = 40)
        val vedtakDato = LinjeFeltPosisjon(start = belop.end, end = 48)
        val gjelderID = LinjeFeltPosisjon(start = vedtakDato.end, end = 59)
        val periodeFom = LinjeFeltPosisjon(start = gjelderID.end, end = 67)
        val periodeTom = LinjeFeltPosisjon(start = periodeFom.end, end = 75)
        val stonadsKode = LinjeFeltPosisjon(start = periodeTom.end, end = 83)
        val referanseGammelSak = LinjeFeltPosisjon(start = stonadsKode.end, end = 101)
        val transaksjonsDato = LinjeFeltPosisjon(start = referanseGammelSak.end, end = 109)
        val enhetBosted = LinjeFeltPosisjon(start = transaksjonsDato.end, end = 113)
        val enhetBehandlende = LinjeFeltPosisjon(start = enhetBosted.end, end = 117)
        val hjemmelKode = LinjeFeltPosisjon(start = enhetBehandlende.end, end = 119)
        val arsakKode = LinjeFeltPosisjon(start = hjemmelKode.end, end = 131)
        val belopRente = LinjeFeltPosisjon(start = arsakKode.end, end = 151)
        val fremtidigYtelse = LinjeFeltPosisjon(start = belopRente.end, end = 162)
        val utbetalDato = LinjeFeltPosisjon(start = fremtidigYtelse.end, end = 170)
        val fagsystemID = LinjeFeltPosisjon(start = utbetalDato.end, end = 200)
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

        fun parseDouble(line: String): Double {
            val amount = parseString(line)
            val integer = amount.dropLast(2)
            val dec = amount.drop(amount.length - 2)

            return "$integer.$dec".toDouble()
        }

        fun parseInt(line: String): Int = parseString(line).toInt()
        fun parseDate(line: String): LocalDate {
            val dateString = parseString(line)
            val dtf = DateTimeFormatter.ofPattern("yyyyMMdd")
            return LocalDate.parse(dateString, dtf)
        }
    }
}
