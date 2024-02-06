package sokos.ske.krav.util

import kotlinx.datetime.toKotlinLocalDate
import sokos.ske.krav.domain.nav.KravLinje
import sokos.ske.krav.domain.ske.requests.AvskrivingRequest
import sokos.ske.krav.domain.ske.requests.NyHovedStolRequest
import sokos.ske.krav.domain.ske.requests.EndreRenteBeloepRequest
import sokos.ske.krav.domain.ske.requests.HovedstolBeloep
import sokos.ske.krav.domain.ske.requests.Kravidentifikatortype
import sokos.ske.krav.domain.ske.requests.NyOppdragsgiversReferanseRequest
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

fun makeOpprettKravRequest(krav: KravLinje, uuid: String): OpprettInnkrevingsoppdragRequest {
    val kravFremtidigYtelse = krav.fremtidigYtelse.toDouble().roundToLong()
    val dtf = DateTimeFormatter.ofPattern("yyyyMMdd")
    val tilleggsinformasjonNav = TilleggsinformasjonNav(
        tilbakeKrevingsPeriode = TilbakeKrevingsPeriode(LocalDate.parse(krav.periodeFOM, dtf).toKotlinLocalDate(), LocalDate.parse(krav.periodeTOM, dtf).toKotlinLocalDate()),
        ytelserForAvregning = YtelseForAvregningBeloep(beloep = kravFremtidigYtelse).takeIf { kravFremtidigYtelse > 0L },
    )

    val beloepRente = krav.belopRente.toDouble().roundToLong()
    val kravtype =  KravtypeMappingFromNAVToSKE.getKravtype(krav)
    val skyldner =
        if (krav.gjelderID.startsWith("00"))
            Skyldner(Skyldner.IdentifikatorType.ORGANISASJON, krav.gjelderID.substring(2, krav.gjelderID.length))
        else Skyldner (Skyldner.IdentifikatorType.PERSON, krav.gjelderID)

    return OpprettInnkrevingsoppdragRequest(
        kravtype = kravtype,
        skyldner = skyldner,
        hovedstol = HovedstolBeloep(valuta = Valuta.NOK, beloep = krav.belop.toDouble().roundToLong()),
        renteBeloep =
        listOf(
            RenteBeloep(
                beloep = beloepRente,
                renterIlagtDato = krav.vedtakDato.toKotlinLocalDate(),
            ),
        ).takeIf { beloepRente > 0L },
        oppdragsgiversReferanse = krav.saksNummer,
        oppdragsgiversKravIdentifikator = uuid,
        fastsettelsesDato = krav.vedtakDato.toKotlinLocalDate(),
        tilleggsInformasjon = tilleggsinformasjonNav,
    )
}

fun makeEndreRenteRequest(krav: KravLinje): EndreRenteBeloepRequest = EndreRenteBeloepRequest(
    listOf(
        RenteBeloep(
            beloep = krav.belopRente.toDouble().roundToLong(),
            renterIlagtDato = krav.vedtakDato.toKotlinLocalDate(),
        ),
    ),
)

fun makeNyHovedStolRequest(krav: KravLinje): NyHovedStolRequest = NyHovedStolRequest(HovedstolBeloep(beloep = krav.belop.toDouble().roundToLong()))

fun lagNyOppdragsgiversReferanseRequest(krav: KravLinje) = NyOppdragsgiversReferanseRequest(krav.saksNummer)
fun makeStoppKravRequest(nyref: String, kravidentifikatortype: Kravidentifikatortype) = AvskrivingRequest(kravidentifikatortype.value, kravidentifikator = nyref)
fun KravLinje.isNyttKrav() = (!this.isEndring() && !this.isStopp())
fun KravLinje.isEndring() = (referanseNummerGammelSak.isNotEmpty() && !isStopp())
fun KravLinje.isStopp() = (belop.toDouble().roundToLong() == 0L)
