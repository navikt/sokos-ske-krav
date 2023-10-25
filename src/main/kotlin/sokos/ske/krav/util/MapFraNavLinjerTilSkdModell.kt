package sokos.ske.krav.util

import kotlinx.datetime.toKotlinLocalDate
import sokos.ske.krav.domain.nav.DetailLine
import sokos.ske.krav.domain.ske.requests.*
import sokos.ske.krav.domain.ske.requests.OpprettInnkrevingsoppdragRequest.Kravtype.TILBAKEKREVINGFEILUTBETALTYTELSE
import kotlin.math.roundToLong

fun lagOpprettKravRequest(krav: DetailLine): OpprettInnkrevingsoppdragRequest {
	val kravFremtidigYtelse = krav.fremtidigYtelse.roundToLong()
	val tilleggsinformasjonNav = TilleggsinformasjonNav(
		stoenadsType = TilleggsinformasjonNav.StoenadsType.from(krav.kravkode).toString(),
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
				renterIlagtDato = krav.vedtakDato.toKotlinLocalDate()
			)
		).takeIf { beloepRente > 0L },
		oppdragsgiversSaksnummer = krav.saksNummer,
		oppdragsgiversKravIdentifikator = krav.saksNummer,
		fastsettelsesDato = krav.vedtakDato.toKotlinLocalDate(),
		tilleggsInformasjon = tilleggsinformasjonNav
	)
}

fun lagEndreKravRequest(krav: DetailLine, nyref: String) =
	EndringRequest(
		kravidentifikator = nyref,
		nyHovedstol = HovedstolBeloep(Valuta.NOK, krav.belop.roundToLong()),
	)


fun lagStoppKravRequest(nyref: String) = AvskrivingRequest(kravidentifikator = nyref)
fun DetailLine.erNyttKrav() = (!this.erEndring() && !this.erStopp())
fun DetailLine.erEndring() = (referanseNummerGammelSak.isNotEmpty() && !erStopp())
fun DetailLine.erStopp() = (belop.roundToLong() == 0L)





