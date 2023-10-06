package sokos.ske.krav.service


import sokos.ske.krav.navmodels.DetailLine
import sokos.ske.krav.skemodels.requests.*
import sokos.ske.krav.skemodels.requests.OpprettInnkrevingsoppdragRequest.Kravtype.TILBAKEKREVINGFEILUTBETALTYTELSE
import kotlin.math.roundToLong

sealed class ValidationResult {
    data class Success(val detailLines: List<DetailLine>) : ValidationResult()
    data class Error(val message: List<String>) : ValidationResult()
}

fun fileValidator(content: List<String>): ValidationResult {
    val firstLine = parseFRtoDataFirsLineClass(content.first())
    val lastLine = parseFRtoDataLastLIneClass(content.last())
    val detailLines = content.subList(1, content.lastIndex).map { parseFRtoDataDetailLineClass(it) }

    val invalidKravkode = detailLines.any {  TilleggsinformasjonNav.Stonadstype.from(it.kravkode) == null }
    val invalidNumberOfLines = lastLine.numTransactionLines != detailLines.size
    val invalidSum = detailLines.sumOf { it.belop + it.belopRente } != lastLine.sumAllTransactionLines
    val invalidTransferDate = firstLine.transferDate != lastLine.transferDate

    //  if(invalidNumberOfLines || invalidSum || invalidTransferDate || invalidKravkode){
    if (invalidNumberOfLines || invalidSum || invalidTransferDate) {
        val errorMessages = mutableListOf<String>()
        if (invalidNumberOfLines) errorMessages.add("Antall krav stemmer ikke med antallet i siste linje!")
        if (invalidSum) errorMessages.add("Sum alle linjer stemmer ikke med sum i siste linje!")
        if (invalidTransferDate) errorMessages.add("Dato sendt er avvikende mellom første og siste linje fra OS!")
        //     if(invalidKravkode) errorMessages.add("Ugyldig kravkode!")

        return ValidationResult.Error(errorMessages)
    }
    return ValidationResult.Success(detailLines)
}

fun lagOpprettKravRequest(krav: DetailLine): OpprettInnkrevingsoppdragRequest {
    val kravFremtidigYtelse = krav.fremtidigYtelse.roundToLong()
    val tilleggsinformasjonNav = TilleggsinformasjonNav(
        stoenadstype = TilleggsinformasjonNav.Stonadstype.from(krav.kravkode).toString(),
        YtelseForAvregningBeloep(beloep = kravFremtidigYtelse).takeIf { kravFremtidigYtelse > 0L }
    )

    val beloepRente = krav.belopRente.roundToLong()

    //val saksnummerForTestRequests = UUID.randomUUID().toString()

    return OpprettInnkrevingsoppdragRequest(
        kravtype = TILBAKEKREVINGFEILUTBETALTYTELSE.value,
        skyldner = Skyldner(Skyldner.Identifikatortype.PERSON, krav.gjelderID),
        hovedstol = HovedstolBeloep(beloep = krav.belop.roundToLong()),
        renteBeloep = arrayOf(
            RenteBeloep(
                beloep = beloepRente,
                renterIlagtDato = krav.vedtakDato
            )
        ).takeIf { beloepRente > 0L },
        oppdragsgiversSaksnummer = krav.saksNummer,
        oppdragsgiversKravidentifikator = krav.saksNummer,
        fastsettelsesdato = krav.vedtakDato,
        tilleggsinformasjon = tilleggsinformasjonNav
    )
}

fun lagEndreKravRequest(krav: DetailLine, nyref: String) =
    EndringRequest(
        kravidentifikator = nyref,
        nyHovedstol = HovedstolBeloep(Valuta.NOK, krav.belop.roundToLong()),
    )


fun lagStoppKravRequest(nyref: String) = AvskrivingRequest(kravidentifikator = nyref)





