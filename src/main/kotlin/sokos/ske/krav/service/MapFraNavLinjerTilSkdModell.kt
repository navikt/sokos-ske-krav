package sokos.ske.krav.service


import sokos.ske.krav.navmodels.DetailLine
import sokos.ske.krav.skemodels.requests.AvskrivingRequest
import sokos.ske.krav.skemodels.requests.EndringRequest
import sokos.ske.krav.skemodels.requests.HovedstolBeloep
import sokos.ske.krav.skemodels.requests.OpprettInnkrevingsoppdragRequest
import sokos.ske.krav.skemodels.requests.OpprettInnkrevingsoppdragRequest.Kravtype.TILBAKEKREVINGFEILUTBETALTYTELSE
import sokos.ske.krav.skemodels.requests.RenteBeloep
import sokos.ske.krav.skemodels.requests.Skyldner
import sokos.ske.krav.skemodels.requests.TilleggsinformasjonNav
import sokos.ske.krav.skemodels.requests.Valuta
import sokos.ske.krav.skemodels.requests.YtelseForAvregningBeloep
import kotlin.math.roundToLong

sealed class ValidationResult {
    data class Success(val detailLines: List<DetailLine>) : ValidationResult()
    data class Error(val message: List<String>) : ValidationResult()
}

fun fileValidator(content: List<String>): ValidationResult {
    val firstLine = parseFRtoDataFirsLineClass(content.first())
    val lastLine = parseFRtoDataLastLIneClass(content.last())
    val detailLines = content.subList(1, content.lastIndex).map { parseFRtoDataDetailLineClass(it) }

    val invalidKravkode = detailLines.any {  TilleggsinformasjonNav.StoenadsType.from(it.kravkode) == null }
    val invalidNumberOfLines = lastLine.numTransactionLines != detailLines.size
    val invalidSum = detailLines.sumOf { it.belop + it.belopRente } != lastLine.sumAllTransactionLines
    val invalidTransferDate = firstLine.transferDate != lastLine.transferDate

    if(invalidNumberOfLines || invalidSum || invalidTransferDate || invalidKravkode){
        val errorMessages = mutableListOf<String>()
        if (invalidNumberOfLines) errorMessages.add("Antall krav stemmer ikke med antallet i siste linje!")
        if (invalidSum) errorMessages.add("Sum alle linjer stemmer ikke med sum i siste linje!")
        if (invalidTransferDate) errorMessages.add("Dato sendt er avvikende mellom første og siste linje fra OS!")
        if(invalidKravkode) errorMessages.add("Ugyldig kravkode!")

        return ValidationResult.Error(errorMessages)
    }
    return ValidationResult.Success(detailLines)
}

fun lagOpprettKravRequest(krav: DetailLine): OpprettInnkrevingsoppdragRequest {
    val kravFremtidigYtelse = krav.fremtidigYtelse.roundToLong()
    val tilleggsinformasjonNav = TilleggsinformasjonNav(
        stoenadsType = TilleggsinformasjonNav.StoenadsType.from(krav.kravkode).toString(),
        YtelseForAvregningBeloep(beloep = kravFremtidigYtelse).takeIf { kravFremtidigYtelse > 0L }
    )

    val beloepRente = krav.belopRente.roundToLong()

    //val saksnummerForTestRequests = UUID.randomUUID().toString()

    return OpprettInnkrevingsoppdragRequest(
        kravtype = TILBAKEKREVINGFEILUTBETALTYTELSE.value,
        skyldner = Skyldner(Skyldner.IdentifikatorType.PERSON, krav.gjelderID),
        hovedstol = HovedstolBeloep(beloep = krav.belop.roundToLong()),
        renteBeloep = arrayOf(
            RenteBeloep(
                beloep = beloepRente,
                renterIlagtDato = krav.vedtakDato
            )
        ).takeIf { beloepRente > 0L },
        oppdragsgiversSaksnummer = krav.saksNummer,
        oppdragsgiversKravIdentifikator = krav.saksNummer,
        fastsettelsesDato = krav.vedtakDato,
        tilleggsInformasjon = tilleggsinformasjonNav
    )
}

fun lagEndreKravRequest(krav: DetailLine, nyref: String) =
    EndringRequest(
        kravidentifikator = nyref,
        nyHovedstol = HovedstolBeloep(Valuta.NOK, krav.belop.roundToLong()),
    )


fun lagStoppKravRequest(nyref: String) = AvskrivingRequest(kravidentifikator = nyref)





