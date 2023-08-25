package sokos.skd.poc

import sokos.skd.poc.navmodels.DetailLine
import sokos.skd.poc.navmodels.FirstLine
import sokos.skd.poc.navmodels.LastLine


fun parseFRtoDataFirsLineClass(line: String): FirstLine {
    val parser = FrParser(line)
    parser.parseString(4)
    return FirstLine(
        transferDate = parser.parseDateTime(14),
        sender = parser.parseString(9),
    )
}

fun parseFRtoDataDetailLineClass(line: String): DetailLine {
    val parser = FrParser(line)
    parser.parseString(4)
    return DetailLine(
        lineNummer = parser.parseInt(7),
        saksNummer = parser.parseString(18),
        belop = parser.parseAmountAsDouble(11),
        vedtakDato = parser.parseDate(8),
        gjelderID = parser.parseString(11),
        periodeFOM = parser.parseString(8),
        periodeTOM = parser.parseString(8),
        kravkode = parser.parseString(8),
        referanseNummerGammelSak = parser.parseString(18),
        transaksjonDato = parser.parseString(8),
        enhetBosted = parser.parseString(4),
        enhetBehandlende = parser.parseString(4),
        kodeHjemmel = parser.parseString(2),
        kodeArsak = parser.parseString(12),
        belopRente = parser.parseAmountAsDouble(20),
        fremtidigYtelse = parser.parseAmountAsDouble(11),
    )
}

fun parseFRtoDataLastLIneClass(line: String): LastLine {
    val parser = FrParser(line)
    parser.parseString(4)
    return LastLine(
        transferDate = parser.parseDateTime(14),
        sender = parser.parseString(9).trim(),
        numTransactionLines = parser.parseInt(8),
        sumAllTransactionLines = parser.parseAmountAsDouble(15),
    )
}

class FrParser(val line: String) {
    private var pos = 0
    fun parseString(len: Int): String {
        if (line.length < pos + len) return line.substring(pos).trim()
        return line.substring(pos, pos + len).trim().also { pos += len }
    }

    fun parseInt(len: Int) = this.parseString(len).toInt()


    fun parseAmountAsDouble(len: Int) = this.parseString(len).trimStart('0')
        .let {
            when (it.length) {
                0 -> "0.00"
                1 -> "0.0" + it
                2 -> "0." + it
                else -> "${it.dropLast(2)}.${it.drop(it.length - 2)}"
            }
        }.toDouble()

    fun parseDate(len: Int): kotlinx.datetime.LocalDate {
        val dateString = parseString(len)
        val year = dateString.substring(0, 4)
        val month = dateString.substring(4, 6)
        val day = dateString.substring(6, 8)

        return kotlinx.datetime.LocalDate.parse("$year-$month-$day")
    }

    fun parseDateTime(len: Int): kotlinx.datetime.LocalDateTime {
        val dateString = parseString(len)
        val year = dateString.substring(0, 4)
        val month = dateString.substring(4, 6)
        val day = dateString.substring(6, 8)
        val hour = dateString.substring(8, 10)
        val minute = dateString.substring(10, 12)
        val second = dateString.substring(12, 14)

        return kotlinx.datetime.LocalDateTime.parse("$year-$month-${day}T$hour:$minute:$second")
    }
}
