package sokos.skd.poc.service


import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import sokos.skd.poc.navmodels.DetailLine
import sokos.skd.poc.navmodels.Stonadstype
import sokos.skd.poc.skdmodels.Avskriving.AvskrivingRequest
import sokos.skd.poc.skdmodels.Avskriving.AvskrivingRequest.Kravidentifikatortype.SKATTEETATENSKRAVIDENTIFIKATOR
import sokos.skd.poc.skdmodels.Endring.EndringRequest
import sokos.skd.poc.skdmodels.NyttOppdrag.*

import sokos.skd.poc.skdmodels.NyttOppdrag.OpprettInnkrevingsoppdragRequest.Kravtype.TILBAKEKREVINGFEILUTBETALTYTELSE
import sokos.skd.poc.skdmodels.NyttOppdrag.TilleggsinformasjonNav.Stoenadstype
import kotlin.math.roundToLong

sealed class ValidationResult{
    data class Success(val detailLines: List<DetailLine>) : ValidationResult()
    data class Error(val message: List<String>) : ValidationResult()
}
fun fileValidator(content: List<String>): ValidationResult {
    val firstLine = parseFRtoDataFirsLineClass(content.first())
    val lastLine = parseFRtoDataLastLIneClass(content.last())
    val detailLines = content.subList(1, content.lastIndex).map {parseFRtoDataDetailLineClass(it)  }

//    val invalidKravkode = detailLines.any {  TilleggsinformasjonNav.Stonadstype.from(it.kravkode) == null }
    val invalidNumberOfLines = lastLine.numTransactionLines != detailLines.size
    val invalidSum = detailLines.sumOf { it.belop + it.belopRente } != lastLine.sumAllTransactionLines
    val invalidTransferDate = firstLine.transferDate != lastLine.transferDate

    //  if(invalidNumberOfLines || invalidSum || invalidTransferDate || invalidKravkode){
    if(invalidNumberOfLines || invalidSum || invalidTransferDate){
        val errorMessages = mutableListOf<String>()
        if(invalidNumberOfLines) errorMessages.add("Antall krav stemmer ikke med antallet i siste linje!")
        if(invalidSum) errorMessages.add("Sum alle linjer stemmer ikke med sum i siste linje!")
        if(invalidTransferDate) errorMessages.add("Dato sendt er avvikende mellom første og siste linje fra OS!")
        //     if(invalidKravkode) errorMessages.add("Ugyldig kravkode!")

        return ValidationResult.Error(errorMessages)
    }
    return ValidationResult.Success(detailLines)
}

fun lagOpprettKravRequest(krav: DetailLine): String {
    if (krav.belopRente.roundToLong() > 0L) println("DENNE::::: ${krav.belopRente}")
    return OpprettInnkrevingsoppdragRequest(
        kravtype = TILBAKEKREVINGFEILUTBETALTYTELSE.value,
        skyldner = Skyldner(Skyldner.Identifikatortype.PERSON, krav.gjelderID),
        hovedstol = HovedstolBeloep(Valuta.NOK, krav.belop.roundToLong()),
        renteBeloep =  arrayOf(
            RenteBeloep(
                valuta = Valuta.NOK,
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
                    valuta = Valuta.NOK,
                    beloep = krav.fremtidigYtelse.roundToLong()
                ).takeIf { krav.fremtidigYtelse.roundToLong() > 0 }
            )
        }
    ).let { Json.encodeToJsonElement(it).toString() }

}

fun lagEndreKravRequest(krav: DetailLine): String {
    return Json.encodeToJsonElement(
        EndringRequest(
        kravidentifikatortype = EndringRequest.Kravidentifikatortype.SKATTEETATENSKRAVIDENTIFIKATOR.value,
        kravidentifikator = krav.saksNummer,
        nyHovedstol = HovedstolBeloep(Valuta.NOK, krav.belop.roundToLong()),
        )
    ).toString()
}

fun lagStoppKravRequest(krav: DetailLine): String {
    return Json.encodeToJsonElement(
        AvskrivingRequest(
        kravidentifikatortype =  SKATTEETATENSKRAVIDENTIFIKATOR.value,
        kravidentifikator = krav.saksNummer
    )
    ).toString()
}
