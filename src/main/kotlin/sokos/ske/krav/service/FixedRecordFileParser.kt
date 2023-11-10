package sokos.ske.krav.service

import sokos.ske.krav.domain.nav.FirstLine
import sokos.ske.krav.domain.nav.KravLinje
import sokos.ske.krav.domain.nav.LastLine
import sokos.ske.krav.service.ForsteLinjeFRFeltPlasseringer.SENDER_FORSTELINJE
import sokos.ske.krav.service.ForsteLinjeFRFeltPlasseringer.TRANSFERDATO_FORSTELINJE
import sokos.ske.krav.service.KravlinjeFRFeltplasseringer.ARSAKKODE
import sokos.ske.krav.service.KravlinjeFRFeltplasseringer.BELOP
import sokos.ske.krav.service.KravlinjeFRFeltplasseringer.BELOPRENTE
import sokos.ske.krav.service.KravlinjeFRFeltplasseringer.ENHETBEHANDLENDE
import sokos.ske.krav.service.KravlinjeFRFeltplasseringer.ENHETBOSTED
import sokos.ske.krav.service.KravlinjeFRFeltplasseringer.FAGSYSTEMID
import sokos.ske.krav.service.KravlinjeFRFeltplasseringer.FREMTIDIGYTELSE
import sokos.ske.krav.service.KravlinjeFRFeltplasseringer.GJELDERID
import sokos.ske.krav.service.KravlinjeFRFeltplasseringer.HJEMMELKODE
import sokos.ske.krav.service.KravlinjeFRFeltplasseringer.LINJENUMMER
import sokos.ske.krav.service.KravlinjeFRFeltplasseringer.LINJETYPE
import sokos.ske.krav.service.KravlinjeFRFeltplasseringer.PERIODEFOM
import sokos.ske.krav.service.KravlinjeFRFeltplasseringer.PERIODETOM
import sokos.ske.krav.service.KravlinjeFRFeltplasseringer.REFERANSEGAMMELSAK
import sokos.ske.krav.service.KravlinjeFRFeltplasseringer.SAKSNUMMER
import sokos.ske.krav.service.KravlinjeFRFeltplasseringer.STONADSKODE
import sokos.ske.krav.service.KravlinjeFRFeltplasseringer.TRANSAKSJONSDATO
import sokos.ske.krav.service.KravlinjeFRFeltplasseringer.UTBETALDATO
import sokos.ske.krav.service.KravlinjeFRFeltplasseringer.VEDTAKDATO
import sokos.ske.krav.service.SisteLinjeFRFeltPlasseringer.ANTALLLINJER
import sokos.ske.krav.service.SisteLinjeFRFeltPlasseringer.SENDER_SISTELINJE
import sokos.ske.krav.service.SisteLinjeFRFeltPlasseringer.SUMALLELINJER
import sokos.ske.krav.service.SisteLinjeFRFeltPlasseringer.TRANSFERDATO_SISTELINJE
import java.time.LocalDate
import java.time.format.DateTimeFormatter


private object ForsteLinjeFRFeltPlasseringer {
    val TRANSFERDATO_FORSTELINJE = 4 to 18
    val SENDER_FORSTELINJE = 18 to 27
}

private object SisteLinjeFRFeltPlasseringer {
    val TRANSFERDATO_SISTELINJE = 4 to 18
    val SENDER_SISTELINJE = 18 to 27
    val ANTALLLINJER = 27 to 35
    val SUMALLELINJER = 35 to 50
}

private object KravlinjeFRFeltplasseringer {
    val LINJETYPE = 0 to 4
    val LINJENUMMER = 4 to 11
    val SAKSNUMMER = 11 to 29
    val BELOP = 29 to 40
    val VEDTAKDATO = 40 to 48
    val GJELDERID = 48 to 59
    val PERIODEFOM = 59 to 67
    val PERIODETOM = 67 to 75
    val STONADSKODE = 75 to 83
    val REFERANSEGAMMELSAK = 83 to 101
    val TRANSAKSJONSDATO = 101 to 109
    val ENHETBOSTED = 109 to 113
    val ENHETBEHANDLENDE = 113 to 117
    val HJEMMELKODE = 117 to 119
    val ARSAKKODE = 119 to 131
    val BELOPRENTE = 131 to 151
    val FREMTIDIGYTELSE = 151 to 162
    val UTBETALDATO = 162 to 170
    val FAGSYSTEMID = 170 to 200
}

fun parseFRtoDataFirsLineClass(line: String) = with(FixedRecordParser(line)) {
    LINJETYPE.parseString()
    FirstLine(
        transferDate = TRANSFERDATO_FORSTELINJE.parseString(),
        sender = SENDER_FORSTELINJE.parseString(),
    )
}

fun parseFRtoDataDetailLineClass(line: String): KravLinje  = with(FixedRecordParser(line)) {
	LINJETYPE.parseInt()
	return KravLinje(
		linjeNummer = LINJENUMMER.parseInt(),
		saksNummer = SAKSNUMMER.parseString(),
		belop = BELOP.parseAmountAsDouble(),
		vedtakDato = VEDTAKDATO.parseToJavaDate(),
		gjelderID = GJELDERID.parseString(),
		periodeFOM = PERIODEFOM.parseString(),
		periodeTOM = PERIODETOM.parseString(),
		stonadsKode = STONADSKODE.parseString(),
		referanseNummerGammelSak = REFERANSEGAMMELSAK.parseString(),
		transaksjonDato = TRANSAKSJONSDATO.parseString(),
		enhetBosted = ENHETBOSTED.parseString(),
		enhetBehandlende = ENHETBEHANDLENDE.parseString(),
		hjemmelKode = HJEMMELKODE.parseString(),
		arsakKode = ARSAKKODE.parseString(),
		belopRente = BELOPRENTE.parseAmountAsDouble(),
		fremtidigYtelse = FREMTIDIGYTELSE.parseAmountAsDouble(),
		utbetalDato = UTBETALDATO.parseString(),
		fagsystemId = FAGSYSTEMID.parseString(),
	)
}

fun parseFRtoDataLastLineClass(line: String) =  with(FixedRecordParser(line)) {
    LINJETYPE.parseString()
    LastLine(
        transferDate = TRANSFERDATO_SISTELINJE.parseString(),
        sender = SENDER_SISTELINJE.parseString().trim(),
        numTransactionLines = ANTALLLINJER.parseInt(),
        sumAllTransactionLines = SUMALLELINJER.parseAmountAsDouble(),
    )
}


class FixedRecordParser(private val line: String) {
    fun Pair<Int, Int>.parseString(): String {
        if (second < first) throw Exception("posisonsangivelse er på trynet")
        if (line.length < first) return ""
        if (line.length < second) return line.substring(first).trim()
        return line.substring(first, second).trim()
    }

    fun Pair<Int, Int>.parseInt() = parseString().toInt()


    fun Pair<Int, Int>.parseAmountAsDouble(): Double {
        val amount = parseString()
        val integer = amount.dropLast(2)
        val dec = amount.drop(amount.length - 2)

        return "$integer.$dec".toDouble()
    }

    fun Pair<Int, Int>.parseToJavaDate(): LocalDate {
        val dateString = parseString()
        val dtf = DateTimeFormatter.ofPattern("yyyyMMdd")
        return LocalDate.parse(dateString, dtf)
    }

}
