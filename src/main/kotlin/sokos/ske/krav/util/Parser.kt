package sokos.ske.krav.util

import kotlinx.datetime.toKotlinLocalDate
import mu.KotlinLogging
import sokos.ske.krav.database.RepositoryExtensions.toKotlinxLocalDateTime
import sokos.ske.krav.domain.DetailLine
import sokos.ske.krav.domain.FirstLine
import sokos.ske.krav.domain.LastLine
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val logger = KotlinLogging.logger {}

fun parseFRtoDataFirsLineClass(line: String): FirstLine {
	val parser = FixedRecordParser(line)
	parser.parseString(4)
	return FirstLine(
		transferDate = parser.parseDateTime(14),
		sender = parser.parseString(9),
	)
}

fun parseFRtoDataDetailLineClass(line: String): DetailLine {
	val parser = FixedRecordParser(line)
	parser.parseString(4)
	return DetailLine(
		lineNummer = parser.parseInt(7),
		saksNummer = parser.parseString(18),
		belop = parser.parseAmountAsDouble(11),
		vedtakDato = parser.parseDate(8)!!,
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
		utbetalDato = parser.parseDate(8),
		fagsystemId = parser.parseString(30)
	)
}

fun parseFRtoDataLastLineClass(line: String): LastLine {
	val parser = FixedRecordParser(line)
	parser.parseString(4)
	return LastLine(
		transferDate = parser.parseDateTime(14),
		sender = parser.parseString(9).trim(),
		numTransactionLines = parser.parseInt(8),
		sumAllTransactionLines = parser.parseAmountAsDouble(15),
	)
}

fun parseDetailLinetoFRData(line: DetailLine) = "0030" +
		prefixString(line.lineNummer, 7) +
		suffixStringWithSpace(line.saksNummer, 18) +
		prefixString(line.belop) +
		line.vedtakDato.year.toString() +
		prefixString(line.vedtakDato.monthNumber, 2) +
		prefixString(line.vedtakDato.dayOfMonth, 2) +
		line.gjelderID +
		line.periodeFOM +
		line.periodeTOM +
		suffixStringWithSpace(line.kravkode, 8) +
		suffixStringWithSpace(line.referanseNummerGammelSak, 18) +
		line.transaksjonDato +
		suffixStringWithSpace(line.enhetBosted, 4) +
		suffixStringWithSpace(line.enhetBehandlende, 4) +
		suffixStringWithSpace(line.kodeHjemmel, 2) +
		suffixStringWithSpace(line.kodeArsak, 12) +
		"         " +
		prefixString(line.belopRente) +
		prefixString(line.fremtidigYtelse)


fun prefixString(field: String, len: Int, prefix: String): String {
	var result: String = field
	while (result.length < len) (prefix + result).also { result = it }
	return result.substring(0, len)
}

fun prefixString(field: Double): String {
	val str: String = field.toString().let {
		val pos = it.indexOf(".")
		if (pos > -1) {
			when (it.length - pos) {
				1 -> it.dropLast(1) + "00"
				2 -> it.dropLast(2) + it.drop(pos + 1) + "0"
				3 -> it.dropLast(3) + it.drop(pos + 1)
				else -> {
					logger.info { "Skal ikke skje" }
					"000000000000"
					//TODO kaste exception ??
				}
			}
		} else it
	}
	return prefixString(str, 11, "0")
}

private fun prefixString(field: Int, len: Int, prefix: String = "0") = prefixString(field.toString(), len, prefix)

fun suffixStringWithSpace(field: String, len: Int): String {
	var result: String = field
	while (result.length < len) ("$result ").also { result = it }
	return result.substring(0, len)
}


class FixedRecordParser(private val line: String) {
	private var pos = 0
	fun parseString(len: Int): String {
		if (line.length < pos) return ""
		if (line.length < pos + len) return line.substring(pos).trim()
		return line.substring(pos, pos + len).trim().also { pos += len }
	}

	fun parseInt(len: Int) = this.parseString(len).toInt()


	fun parseAmountAsDouble(len: Int) = this.parseString(len).trimStart('0')
		.let {
			when (it.length) {
				0 -> "0.00"
				1 -> "0.0$it"
				2 -> "0.$it"
				else -> "${it.dropLast(2)}.${it.drop(it.length - 2)}"
			}
		}.toDouble()

	fun parseDate(len: Int): kotlinx.datetime.LocalDate? {
		if (line.length < pos + 1) return null
		val dateString = parseString(len)
		val dtf = DateTimeFormatter.ofPattern("yyyyMMdd")
		return java.time.LocalDate.parse(dateString, dtf).toKotlinLocalDate()
	}

	fun parseDateTime(len: Int): kotlinx.datetime.LocalDateTime {
		val dateString = parseString(len)
		val dtfCustom = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
		return LocalDateTime.parse(dateString, dtfCustom).toKotlinxLocalDateTime()
	}
}
