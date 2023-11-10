package sokos.ske.krav.util

import sokos.ske.krav.domain.nav.FirstLine
import sokos.ske.krav.domain.nav.KravLinje
import sokos.ske.krav.domain.nav.LastLine

import java.time.LocalDate
import java.time.format.DateTimeFormatter

fun parseFRtoDataFirsLineClass(line: String): FirstLine {
    val parser = FixedRecordParser(line)
    parser.parseString(FieldLengths.LINJETYPEID)
    return FirstLine(
        transferDate = parser.parseString(FieldLengths.DATE_TIME),
        sender = parser.parseString(FieldLengths.SENDER),
    )
}

fun parseFRtoDataDetailLineClass(line: String): KravLinje {
    val parser = FixedRecordParser(line)
    parser.parseString(FieldLengths.LINJETYPEID)
    return KravLinje(
        linjeNummer = parser.parseInt(FieldLengths.LINJE_NUMMER),
        saksNummer = parser.parseString(FieldLengths.SAKS_NUMMER),
        belop = parser.parseAmountAsDouble(FieldLengths.LINJE_BELOP),
        vedtakDato = parser.parseDate(FieldLengths.DATE),
        gjelderID = parser.parseString(FieldLengths.GJELDER_ID),
        periodeFOM = parser.parseString(FieldLengths.DATE),
        periodeTOM = parser.parseString(FieldLengths.DATE),
        stonadsKode = parser.parseString(FieldLengths.STONADSKODE),
        referanseNummerGammelSak = parser.parseString(FieldLengths.REFERANSENR_GAMMEL_SAK),
        transaksjonDato = parser.parseString(FieldLengths.DATE),
        enhetBosted = parser.parseString(FieldLengths.ENHET_BOSTED),
        enhetBehandlende = parser.parseString(FieldLengths.ENHET_BEHANDLENDE),
        hjemmelKode = parser.parseString(FieldLengths.HJEMMEL_KODE),
        arsakKode = parser.parseString(FieldLengths.ARSAK_KODE),
        belopRente = parser.parseAmountAsDouble(FieldLengths.BELOP_RENTE),
        fremtidigYtelse = parser.parseAmountAsDouble(FieldLengths.FREMTIDIG_YTELSE),
        utbetalDato = parser.parseString(FieldLengths.DATE),
        fagsystemId = parser.parseString(FieldLengths.FAGSYSTEM_ID),
    )
}

fun parseFRtoDataLastLineClass(line: String): LastLine {
    val parser = FixedRecordParser(line)
    parser.parseString(FieldLengths.LINJETYPEID)
    return LastLine(
        transferDate = parser.parseString(FieldLengths.DATE_TIME),
        sender = parser.parseString(FieldLengths.SENDER).trim(),
        numTransactionLines = parser.parseInt(FieldLengths.ANTALL_TRANSAKSJONER),
        sumAllTransactionLines = parser.parseAmountAsDouble(FieldLengths.SUM_ALLE_TRANSAKSJONER),
    )
}

private object FieldLengths {
    const val LINJETYPEID = 4
    const val DATE_TIME = 14
    const val DATE = 8
    const val SENDER = 9
    const val ANTALL_TRANSAKSJONER = 8
    const val SUM_ALLE_TRANSAKSJONER = 15
    const val LINJE_NUMMER = 7
    const val SAKS_NUMMER = 18
    const val LINJE_BELOP = 11
    const val GJELDER_ID = 11
    const val STONADSKODE = 8
    const val REFERANSENR_GAMMEL_SAK = 18
    const val ENHET_BOSTED = 4
    const val ENHET_BEHANDLENDE = 4
    const val HJEMMEL_KODE = 2
    const val ARSAK_KODE = 12
    const val BELOP_RENTE = 20
    const val FREMTIDIG_YTELSE = 11
    const val FAGSYSTEM_ID = 30
}

class FixedRecordParser(private val line: String) {
    private var pos = 0
    fun parseString(len: Int): String {
        return if (line.length < pos) ""
        else if (line.length < pos + len) line.substring(pos).trim()
        else line.substring(pos, pos + len).trim().also { pos += len }
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
