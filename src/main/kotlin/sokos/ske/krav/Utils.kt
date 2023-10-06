package sokos.ske.krav

import sokos.ske.krav.navmodels.DetailLine


fun prefixString(field: String, len: Int, prefix: String): String {
    var result: String = field
    while (result.length < len) (prefix + result).also { result = it }
    return result.substring(0, len)
}

fun prefixString(field: Double, len: Int, prefix: String): String  {
    val str: String = field.toString().let {
        val pos = it.indexOf(".")
        if (pos > -1) {
            when (it.length - pos) {
                1 -> it.dropLast(1)+"00"
                2 -> it.dropLast(2) + it.drop(pos + 1)+"0"
                3 -> it.dropLast(3) + it.drop(pos + 1)
                else -> {
                    println("Skal ikke skje")
                    "000000000000"
                    //TODO kaste exception ??
                }
            }
        }else it
    }
    return prefixString(str, 11, "0")
}

fun prefixString(field: Int, len: Int, prefix: String) = prefixString(field.toString(), len, prefix)

fun suffixStringWithSpace(field: String, len: Int): String {
    var result: String = field
    while (result.length < len) (result + " ").also { result = it }
    return result.substring(0, len)
}


fun replaceSaksnrInDetailline(line: DetailLine, nyref:String):DetailLine =
        DetailLine(
            lineNummer = line.lineNummer,
            saksNummer = nyref,
            belop = line.belop,
            vedtakDato = line.vedtakDato,
            gjelderID = line.gjelderID,
            periodeFOM = line.periodeFOM,
            periodeTOM = line.periodeTOM,
            kravkode = line.kravkode,
            referanseNummerGammelSak = line.referanseNummerGammelSak,
            transaksjonDato = line.transaksjonDato,
            enhetBosted = line.enhetBosted,
            enhetBehandlende = line.enhetBehandlende,
            kodeHjemmel = line.kodeHjemmel,
            kodeArsak = line.kodeArsak,
            belopRente = line.belopRente,
            fremtidigYtelse = line.fremtidigYtelse
        )

fun replaceRefGammelSakInDetailline(line: DetailLine, nyref:String):DetailLine =
    DetailLine(
        lineNummer = line.lineNummer,
        saksNummer = line.saksNummer,
        belop = line.belop,
        vedtakDato = line.vedtakDato,
        gjelderID = line.gjelderID,
        periodeFOM = line.periodeFOM,
        periodeTOM = line.periodeTOM,
        kravkode = line.kravkode,
        referanseNummerGammelSak = nyref,
        transaksjonDato = line.transaksjonDato,
        enhetBosted = line.enhetBosted,
        enhetBehandlende = line.enhetBehandlende,
        kodeHjemmel = line.kodeHjemmel,
        kodeArsak = line.kodeArsak,
        belopRente = line.belopRente,
        fremtidigYtelse = line.fremtidigYtelse
    )


