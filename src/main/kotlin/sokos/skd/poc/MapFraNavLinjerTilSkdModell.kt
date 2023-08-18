package sokos.skd.poc

import sokos.skd.poc.navmodels.DetailLine
import sokos.skd.poc.navmodels.FirstLine
import sokos.skd.poc.navmodels.LastLine
import sokos.skd.poc.skdmodels.*
import sokos.skd.poc.skdmodels.OpprettInnkrevingsoppdragRequest.Kravtype.TILBAKEKREVINGFEILUTBETALTYTELSE
import sokos.skd.poc.skdmodels.Skyldner.Identifikatortype.PERSON
import kotlin.math.roundToLong

fun mapFraNavTilSkd(navLines: List<String>): List<OpprettInnkrevingsoppdragRequest> {
    val firstLine = parseFRtoDataFirsLineClass(navLines.first())
    val lastLine = parseFRtoDataLastLIneClass(navLines.last())
    val detailLines = arrayOfDetailLines(navLines.subList(1, navLines.lastIndex))

    validateLines(firstLine, lastLine, detailLines)

    return mapAlleKravTilSkdModel(detailLines)
}

fun validateLines(first: FirstLine, lastLine: LastLine, details: List<DetailLine>) {
    var sumAll = 0.0
    assert(lastLine.numTransactionLines.equals(details.size)) { "Antall krav stemmer ikke med antallet i siste linje!"}
    details.forEach { sumAll += it.belop+it.belopRente }
    assert(sumAll.equals(lastLine.sumAllTransactionLines)) { "Sum alle linjer stemmer ikke med sum i siste linje!"}
    assert(first.transferDate.equals(lastLine.transferDate)) { "Dato sendt er avvikende mellom første og siste linje fra OS!"}
}

private fun mapAlleKravTilSkdModel(detailLines: List<DetailLine>): List<OpprettInnkrevingsoppdragRequest> {
    val krav = mutableListOf<OpprettInnkrevingsoppdragRequest>()

    detailLines.forEach {
        val trekk = OpprettInnkrevingsoppdragRequest(
            kravtype = TILBAKEKREVINGFEILUTBETALTYTELSE,
            skyldner = Skyldner(PERSON, it.gjelderID),
            hovedstol = HovedstolBeloep(HovedstolBeloep.Valuta.NOK, it.belop.roundToLong()),
            renteBeloep = arrayOf(
                RenteBeloep(
                    valuta = RenteBeloep.Valuta.NOK,
                    beloep = it.belopRente.roundToLong(),
                    renterIlagtTidspunkt = it.vedtakDato
                )
            ),
            oppdragsgiversSaksnummer = it.saksNummer,
            oppdragsgiversKravidentifikator = it.saksNummer,
            fastsettelsesdato = it.vedtakDato,
            tilleggsinformasjon = (TilleggsinformasjonNav.Stoenadstype from it.kravkode)?.let { it1 ->
                TilleggsinformasjonNav(
                    stoenadstype = it1,
                    referanseGammelSak = it.takeIf { it.referanseNummerGammelSak.isNotEmpty() }?.referanseNummerGammelSak,
                    ytelserForAvregning = YtelseForAvregningBeloep(
                        valuta = YtelseForAvregningBeloep.Valuta.NOK,
                        beloep = it.fremtidigYtelse.roundToLong()
                        )
                )
            }
        )
        krav.add(trekk)
    }
    return krav
}

fun arrayOfDetailLines(lines: List<String>): List<DetailLine> {
    val detailLines = mutableListOf<DetailLine>()
    lines.forEach { detailLines.add(parseFRtoDataDetailLineClass(it)) }
    return detailLines
}
