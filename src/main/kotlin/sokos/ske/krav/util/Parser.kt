package sokos.ske.krav.util

import sokos.ske.krav.domain.nav.FirstLine
import sokos.ske.krav.domain.nav.KravLinje
import sokos.ske.krav.domain.nav.LastLine
import java.time.LocalDate
import java.time.format.DateTimeFormatter

fun parseFRtoDataFirsLineClass(line: String): FirstLine {
    val parser = FixedRecordParser(line)
    parser.parseString(4)
    return FirstLine(
        transferDate = parser.parseString(14),
        sender = parser.parseString(9),
    )
}

fun parseFRtoDataDetailLineClass(line: String): KravLinje {
    val parser = FixedRecordParser(line)
    parser.parseString(4)
    return KravLinje(
        linjeNummer = parser.parseInt(7),
        saksNummer = parser.parseString(18),
        belop = parser.parseAmountAsDouble(11),
        vedtakDato = parser.parseDate(8),
        gjelderID = parser.parseString(11),
        periodeFOM = parser.parseString(8),
        periodeTOM = parser.parseString(8),
        stonadsKode = parser.parseString(8),
        referanseNummerGammelSak = parser.parseString(18),
        transaksjonDato = parser.parseString(8),
        enhetBosted = parser.parseString(4),
        enhetBehandlende = parser.parseString(4),
        hjemmelKode = parser.parseString(2),
        arsakKode = parser.parseString(12),
        belopRente = parser.parseAmountAsDouble(20),
        fremtidigYtelse = parser.parseAmountAsDouble(11),
        utbetalDato = parser.parseString(8),
        fagsystemId = parser.parseString(30),
    )
}

fun parseFRtoDataLastLineClass(line: String): LastLine {
    val parser = FixedRecordParser(line)
    parser.parseString(4)
    return LastLine(
        transferDate = parser.parseString(14),
        sender = parser.parseString(9).trim(),
        numTransactionLines = parser.parseInt(8),
        sumAllTransactionLines = parser.parseAmountAsDouble(15),
    )
}


class FixedRecordParser(private val line: String) {
    private var pos = 0
    fun parseString(len: Int): String {
        if (line.length < pos) return ""
        if (line.length < pos + len) return line.substring(pos).trim()
        return line.substring(pos, pos + len).trim().also { pos += len }
    }

    fun parseInt(len: Int) = this.parseString(len).toInt()


    fun parseAmountAsDouble(len: Int): Double {
        val amount = parseString(len)
        val integer = amount.dropLast(2)
        val dec = amount.drop(amount.length - 2)

        return "$integer.$dec".toDouble()
    }

    fun parseDate(len: Int): LocalDate {
        val dateString = parseString(len)
        val dtf = DateTimeFormatter.ofPattern("yyyyMMdd")
        return LocalDate.parse(dateString, dtf)
    }

}
