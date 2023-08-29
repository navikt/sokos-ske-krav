package sokos.skd.poc

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import sokos.skd.poc.navmodels.DetailLine
import sokos.skd.poc.navmodels.FirstLine
import sokos.skd.poc.navmodels.LastLine
import sokos.skd.poc.navmodels.Stonadstype
import sokos.skd.poc.skdmodels.*
import sokos.skd.poc.skdmodels.AvskrivingRequest.Kravidentifikatortype.SKATTEETATENSKRAVIDENTIFIKATOR
import sokos.skd.poc.skdmodels.HovedstolBeloep.Valuta.NOK
import sokos.skd.poc.skdmodels.OpprettInnkrevingsoppdragRequest.Kravtype.TILBAKEKREVINGFEILUTBETALTYTELSE
import sokos.skd.poc.skdmodels.Skyldner.Identifikatortype.PERSON
import sokos.skd.poc.skdmodels.TilleggsinformasjonNav.Stoenadstype
import kotlin.math.roundToLong

fun mapFraFRTilDetailAndValidate(navLines: List<String>): List<DetailLine> {
    val firstLine = parseFRtoDataFirsLineClass(navLines.first())
    val lastLine = parseFRtoDataLastLIneClass(navLines.last())
    val detailLines = mapToDetailLines(navLines.subList(1, navLines.lastIndex))

    validateLines(firstLine, lastLine, detailLines)

    return detailLines
}

fun validateLines(first: FirstLine, lastLine: LastLine, details: List<DetailLine>) {
    var sumAll = 0.0
    assert(lastLine.numTransactionLines.equals(details.size)) { "Antall krav stemmer ikke med antallet i siste linje!" }
    details.forEach { sumAll += it.belop + it.belopRente }
    assert(sumAll.equals(lastLine.sumAllTransactionLines)) { "Sum alle linjer stemmer ikke med sum i siste linje!" }
    assert(first.transferDate.equals(lastLine.transferDate)) { "Dato sendt er avvikende mellom første og siste linje fra OS!" }
}

fun lagOpprettKravRequest(krav: DetailLine): String {
    if (krav.belopRente.roundToLong() > 0L) println("DENNE::::: ${krav.belopRente}")
    return OpprettInnkrevingsoppdragRequest(
        kravtype = TILBAKEKREVINGFEILUTBETALTYTELSE.value,
        skyldner = Skyldner(PERSON, krav.gjelderID),
        hovedstol = HovedstolBeloep(NOK, krav.belop.roundToLong()),
        renteBeloep =  arrayOf(
            RenteBeloep(
                valuta = RenteBeloep.Valuta.NOK,
                beloep = krav.belopRente.roundToLong(),
                renterIlagtDato = krav.vedtakDato
            )
        ).takeIf { krav.belopRente.roundToLong() > 0L },
        oppdragsgiversSaksnummer = krav.saksNummer,
        oppdragsgiversKravidentifikator = krav.saksNummer,
        fastsettelsesdato = krav.vedtakDato,
        tilleggsinformasjon = (Stonadstype from krav.kravkode)?.let { st ->
            TilleggsinformasjonNav(
                stoenadstype = Stoenadstype.valueOf(st.name).value,
                ytelserForAvregning = YtelseForAvregningBeloep(
                    valuta = YtelseForAvregningBeloep.Valuta.NOK,
                    beloep = krav.fremtidigYtelse.roundToLong()
                ).takeIf { krav.fremtidigYtelse.roundToLong() > 0 }
            )
        }
    ).let { Json.encodeToJsonElement(it).toString() }

}

fun lagEndreKravRequest(krav: DetailLine): String {
    return Json.encodeToJsonElement(EndringRequest(
        kravidentifikatortype = EndringRequest.Kravidentifikatortype.SKATTEETATENSKRAVIDENTIFIKATOR.value,
        kravidentifikator = krav.saksNummer,
        nyHovedstol = HovedstolBeloep(NOK, krav.belop.roundToLong()),
        )).toString()
}

fun lagStoppKravRequest(krav: DetailLine): String {
    return Json.encodeToJsonElement(AvskrivingRequest(
        kravidentifikatortype =  SKATTEETATENSKRAVIDENTIFIKATOR.value,
        kravidentifikator = krav.saksNummer
    )).toString()
}

fun mapToDetailLines(lines: List<String>): List<DetailLine> {
    val detailLines = mutableListOf<DetailLine>()
    lines.forEach { detailLines.add(parseFRtoDataDetailLineClass(it)) }
    return detailLines
}


