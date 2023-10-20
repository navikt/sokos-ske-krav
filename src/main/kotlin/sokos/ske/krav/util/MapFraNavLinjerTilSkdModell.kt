package sokos.ske.krav.util

import sokos.ske.krav.api.model.requests.AvskrivingRequest
import sokos.ske.krav.api.model.requests.EndringRequest
import sokos.ske.krav.api.model.requests.HovedstolBeloep
import sokos.ske.krav.api.model.requests.OpprettInnkrevingsoppdragRequest
import sokos.ske.krav.api.model.requests.OpprettInnkrevingsoppdragRequest.Kravtype.TILBAKEKREVINGFEILUTBETALTYTELSE
import sokos.ske.krav.api.model.requests.RenteBeloep
import sokos.ske.krav.api.model.requests.Skyldner
import sokos.ske.krav.api.model.requests.TilleggsinformasjonNav
import sokos.ske.krav.api.model.requests.Valuta
import sokos.ske.krav.api.model.requests.YtelseForAvregningBeloep
import sokos.ske.krav.domain.DetailLine
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




