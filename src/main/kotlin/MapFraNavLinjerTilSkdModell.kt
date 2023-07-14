import io.swagger.client.models.*
import io.swagger.client.models.OpprettInnkrevingsoppdragRequest.Kravtype.TILBAKEKREVINGFEILUTBETALTYTELSE
import io.swagger.client.models.Skyldner.Identifikatortype.PERSON
import navmodels.DetailLine
import navmodels.FirstLine
import navmodels.LastLine
import sokos.skd.poc.parseFRtoDataDetailLineClass
import sokos.skd.poc.parseFRtoDataFirsLineClass
import sokos.skd.poc.parseFRtoDataLastLIneClass
import sokos.skd.poc.readFileFromOS
import kotlin.math.roundToLong

fun mapFraNavTilSkd(filename: String): List<OpprettInnkrevingsoppdragRequest> {
    val navLines = readFileFromOS(filename)
    val navFirstLine = parseFRtoDataFirsLineClass(navLines.first())
    val navLastLine = parseFRtoDataLastLIneClass(navLines.last())
    val detailLines = arrayOfDetailLines(navLines.subList(1, navLines.lastIndex))

    validateLines(navFirstLine, navLastLine, detailLines)

    val alleKrav = hentAlleKrav(detailLines)
    var sumAll = 0.0
    alleKrav.forEach {
        sumAll += it.hovedstol.beloep
        it.renteBeloep.forEach { sumAll += it.beloep }
    }
    println("Allekrav Sum: $sumAll, Antall: ${alleKrav.size}")
    return alleKrav
}

fun validateLines(first: FirstLine, lastLine: LastLine, details: List<DetailLine>) {
    var sumAll = 0.0
    assert(lastLine.numTransactionLines.equals(details.size))
    println("assertion 1 OK: siste.antall: ${lastLine.numTransactionLines}, antall i arr: ${details.size}")
    details.forEach { sumAll += it.belop+it.belopRente }
    assert(sumAll.equals(lastLine.sumAllTransactionLines))
    println("assertion 2 OK: siste.sum: ${lastLine.sumAllTransactionLines}, sum i arr: ${sumAll}")
}

private fun hentAlleKrav(detailLines: List<DetailLine>): List<OpprettInnkrevingsoppdragRequest> {
    var krav = mutableListOf<OpprettInnkrevingsoppdragRequest>()

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
