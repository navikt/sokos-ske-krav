import io.swagger.client.models.*
import io.swagger.client.models.OpprettInnkrevingsoppdragRequest.Kravtype.TILBAKEKREVINGFEILUTBETALTYTELSE
import io.swagger.client.models.Skyldner.Identifikatortype.PERSON
import navmodels.DetailLine
import navmodels.FirstLine
import navmodels.LastLine
import sokos.skd.poc.parseFRtoDataDetailLineClass
import sokos.skd.poc.parseFRtoDataFirsLineClass
import sokos.skd.poc.parseFRtoDataLastLIneClass
import kotlin.math.roundToLong

fun mapFraNavTilSkd(navLines: List<String>): List<OpprettInnkrevingsoppdragRequest> {
    val firstLine = parseFRtoDataFirsLineClass(navLines.first())
    val lastLine = parseFRtoDataLastLIneClass(navLines.last())
    val detailLines = arrayOfDetailLines(navLines.subList(1, navLines.lastIndex))

    validateLines(firstLine, lastLine, detailLines)

    val alleKrav = mapAlleKravTilSkdModel(detailLines)
    var sumAll = 0.0
    alleKrav.forEach {
        sumAll += it.hovedstol.beloep
        it.renteBeloep.forEach { sumAll += it.beloep }
    }
    assert(alleKrav.size.equals(detailLines.size) ) { "Det er forskjellige antall linje fra OS og antall som sendes SKD!"}
    return alleKrav
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
            renteBeloep = arrayOf<RenteBeloep>(
                RenteBeloep(
                    valuta = RenteBeloep.Valuta.NOK,
                    beloep = it.belopRente.roundToLong(),
                    renterIlagtTidspunkt = it.vedtakDato
                )
            ),
            oppdragsgiversSaksnummer = it.saksNummer,
            oppdragsgiversKravidentifikator = it.kravkode,
            fastsettelsesdato = it.vedtakDato,
            tilleggsinformasjon = it.takeIf { it.referanseNummerGammelSak.isNotEmpty() }
                .let {
                    TilleggsinformasjonNav(
                        stoenadstype = TilleggsinformasjonNav.Stoenadstype.DAGPENGER,
                        referanseGammelSak = it?.referanseNummerGammelSak,
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
