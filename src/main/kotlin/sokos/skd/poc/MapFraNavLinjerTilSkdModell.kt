package sokos.skd.poc

import sokos.skd.poc.navmodels.DetailLine
import sokos.skd.poc.navmodels.FirstLine
import sokos.skd.poc.navmodels.LastLine
import sokos.skd.poc.navmodels.Stonadstype
import sokos.skd.poc.skdmodels.*
import sokos.skd.poc.skdmodels.OpprettInnkrevingsoppdragRequest.Kravtype.TILBAKEKREVINGFEILUTBETALTYTELSE
import sokos.skd.poc.skdmodels.Skyldner.Identifikatortype.PERSON
import sokos.skd.poc.skdmodels.TilleggsinformasjonNav.Stoenadstype
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
    assert(lastLine.numTransactionLines.equals(details.size)) { "Antall krav stemmer ikke med antallet i siste linje!" }
    details.forEach { sumAll += it.belop + it.belopRente }
    assert(sumAll.equals(lastLine.sumAllTransactionLines)) { "Sum alle linjer stemmer ikke med sum i siste linje!" }
    assert(first.transferDate.equals(lastLine.transferDate)) { "Dato sendt er avvikende mellom første og siste linje fra OS!" }
}

private fun mapAlleKravTilSkdModel(detailLines: List<DetailLine>): List<OpprettInnkrevingsoppdragRequest> {
    val nyeKrav = mutableListOf<OpprettInnkrevingsoppdragRequest>()
    val endringKrav = mutableListOf<EndringRequest>()
    val stoppKrav = mutableListOf<AvskrivingRequest>()

    detailLines.forEach {
//        when {
//            it.erStopp() -> stoppKrav.add(lagStoppKravRequest(it))
//            it.erEndring() -> endringKrav.add(lagEndreKravRequest(it))
//            else -> nyeKrav.add(lagOpprettKravRequest(it))
//        }
        nyeKrav.add(lagOpprettKravRequest(it))
    }
    return nyeKrav
}

private fun lagOpprettKravRequest(krav: DetailLine): OpprettInnkrevingsoppdragRequest {
    return OpprettInnkrevingsoppdragRequest(
        kravtype = TILBAKEKREVINGFEILUTBETALTYTELSE.value,
        skyldner = Skyldner(PERSON, krav.gjelderID),
        hovedstol = HovedstolBeloep(HovedstolBeloep.Valuta.NOK, krav.belop.roundToLong()),
        renteBeloep = emptyArray(),
//        renteBeloep =  arrayOf(
//            RenteBeloep(
//                valuta = Valuta.NOK,
//                beloep = krav.belopRente.roundToLong(),
//                renterIlagtDato = krav.vedtakDato
//            ).takeIf { it > 0 }
//        ) ,
        oppdragsgiversSaksnummer = krav.saksNummer,
        oppdragsgiversKravidentifikator = krav.saksNummer,
        fastsettelsesdato = krav.vedtakDato,
        tilleggsinformasjon = (Stonadstype from krav.kravkode)?.let { st ->
            TilleggsinformasjonNav(
                stoenadstype = Stoenadstype.valueOf(st.name).value,
                ytelserForAvregning = YtelseForAvregningBeloep(
                    valuta = YtelseForAvregningBeloep.Valuta.NOK,
                    beloep = krav.fremtidigYtelse.roundToLong()
                )
            )
        }
    )

}

private fun lagEndreKravRequest(krav: DetailLine) {

}

private fun lagStoppKravRequest(krav: DetailLine) {

}

fun arrayOfDetailLines(lines: List<String>): List<DetailLine> {
    val detailLines = mutableListOf<DetailLine>()
    lines.forEach { detailLines.add(parseFRtoDataDetailLineClass(it)) }
    return detailLines
}

private fun DetailLine.erEndring(): Boolean {
    return false
}

private fun DetailLine.erStopp(): Boolean {
    return false
}

