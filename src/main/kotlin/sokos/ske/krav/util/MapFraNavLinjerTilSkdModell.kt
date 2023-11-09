package sokos.ske.krav.util

import sokos.ske.krav.domain.nav.KravLinje
import sokos.ske.krav.domain.ske.requests.AvskrivingRequest
import sokos.ske.krav.domain.ske.requests.EndringRequest
import sokos.ske.krav.domain.ske.requests.HovedstolBeloep
import sokos.ske.krav.domain.ske.requests.OpprettInnkrevingsoppdragRequest
import sokos.ske.krav.domain.ske.requests.OpprettInnkrevingsoppdragRequest.Kravtype.TILBAKEKREVINGFEILUTBETALTYTELSE
import sokos.ske.krav.domain.ske.requests.RenteBeloep
import sokos.ske.krav.domain.ske.requests.Skyldner
import sokos.ske.krav.domain.ske.requests.TilleggsinformasjonNav
import sokos.ske.krav.domain.ske.requests.Valuta
import sokos.ske.krav.domain.ske.requests.YtelseForAvregningBeloep
import kotlin.math.roundToLong

fun lagOpprettKravRequest(krav: KravLinje): OpprettInnkrevingsoppdragRequest {
    val kravFremtidigYtelse = krav.fremtidigYtelse.roundToLong()
    val tilleggsinformasjonNav = TilleggsinformasjonNav(
        stoenadsType = TilleggsinformasjonNav.StoenadsType.from(krav.stonadsKode).toString(),
        YtelseForAvregningBeloep(beloep = kravFremtidigYtelse).takeIf { kravFremtidigYtelse > 0L }
    )

    val beloepRente = krav.belopRente.roundToLong()

    return OpprettInnkrevingsoppdragRequest(
        kravtype = TILBAKEKREVINGFEILUTBETALTYTELSE.value,
        skyldner = Skyldner(Skyldner.IdentifikatorType.PERSON, krav.gjelderID),
        hovedstol = HovedstolBeloep(beloep = krav.belop.roundToLong()),
        renteBeloep = listOf(
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

fun lagEndreKravRequest(krav: KravLinje, nyref: String) =
    EndringRequest(
        kravidentifikator = nyref,
        nyHovedstol = HovedstolBeloep(Valuta.NOK, krav.belop.roundToLong()),
    )


fun lagStoppKravRequest(nyref: String) = AvskrivingRequest(kravidentifikator = nyref)
fun KravLinje.erNyttKrav() = (!this.erEndring() && !this.erStopp())
fun KravLinje.erEndring() = (referanseNummerGammelSak.isNotEmpty() && !erStopp())
fun KravLinje.erStopp() = (belop.roundToLong() == 0L)





