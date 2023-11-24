package sokos.ske.krav.util

import kotlinx.datetime.toKotlinLocalDate
import sokos.ske.krav.domain.nav.KravLinje
import sokos.ske.krav.domain.ske.requests.AvskrivingRequest
import sokos.ske.krav.domain.ske.requests.EndreHovedStolRequest
import sokos.ske.krav.domain.ske.requests.EndreRenterRequest
import sokos.ske.krav.domain.ske.requests.HovedstolBeloep
import sokos.ske.krav.domain.ske.requests.OpprettInnkrevingsoppdragRequest
import sokos.ske.krav.domain.ske.requests.RenteBeloep
import sokos.ske.krav.domain.ske.requests.Skyldner
import sokos.ske.krav.domain.ske.requests.TilbakeKrevingsPeriode
import sokos.ske.krav.domain.ske.requests.TilleggsinformasjonNav
import sokos.ske.krav.domain.ske.requests.Valuta
import sokos.ske.krav.domain.ske.requests.YtelseForAvregningBeloep
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToLong

fun lagOpprettKravRequest(krav: KravLinje): OpprettInnkrevingsoppdragRequest {
    val kravFremtidigYtelse = krav.fremtidigYtelse.roundToLong()
    val dtf = DateTimeFormatter.ofPattern("yyyyMMdd")
    val tilleggsinformasjonNav = TilleggsinformasjonNav(
        tilbakeKrevingsPeriode = TilbakeKrevingsPeriode(LocalDate.parse(krav.periodeFOM, dtf).toKotlinLocalDate(), LocalDate.parse(krav.periodeTOM, dtf).toKotlinLocalDate()),
        ytelserForAvregning = YtelseForAvregningBeloep(beloep = kravFremtidigYtelse).takeIf { kravFremtidigYtelse > 0L },
    )

    val beloepRente = krav.belopRente.roundToLong()
    val stonadstypekode = StoenadstypeKodeNAV.fromString(krav.stonadsKode)
    val hjemmelkodePak = HjemmelkodePak.valueOf(krav.hjemmelKode)
    val kravtype = NAVKravtypeMapping.getKravtype(stonadstypekode, hjemmelkodePak)

    return OpprettInnkrevingsoppdragRequest(
        kravtype = kravtype,
        skyldner = Skyldner(Skyldner.IdentifikatorType.PERSON, krav.gjelderID),
        hovedstol = HovedstolBeloep(valuta = Valuta.NOK, beloep = krav.belop.roundToLong()),
        renteBeloep =
        listOf(
            RenteBeloep(
                beloep = beloepRente,
                renterIlagtDato = krav.vedtakDato.toKotlinLocalDate(),
            ),
        ).takeIf { beloepRente > 0L },
        oppdragsgiversReferanse = krav.saksNummer,
        oppdragsgiversKravIdentifikator = krav.saksNummer,
        fastsettelsesDato = krav.vedtakDato.toKotlinLocalDate(),
        tilleggsInformasjon = tilleggsinformasjonNav,
    )
}

fun lagEndreRenteRequest(krav: KravLinje): EndreRenterRequest = EndreRenterRequest(
    listOf(
        RenteBeloep(
            beloep = krav.belopRente.roundToLong(),
            renterIlagtDato = krav.vedtakDato.toKotlinLocalDate(),
        ),
    ),
)

fun lagEndreHovedStolRequest(krav: KravLinje): EndreHovedStolRequest = EndreHovedStolRequest(HovedstolBeloep(beloep = krav.belop.roundToLong()))

fun lagStoppKravRequest(nyref: String) = AvskrivingRequest(kravidentifikator = nyref)
fun KravLinje.erNyttKrav() = (!this.erEndring() && !this.erStopp())
fun KravLinje.erEndring() = (referanseNummerGammelSak.isNotEmpty() && !erStopp())
fun KravLinje.erStopp() = (belop.roundToLong() == 0L)
