package sokos.ske.krav.util

import kotlinx.datetime.toKotlinLocalDate
import sokos.ske.krav.domain.nav.KravLinje
import sokos.ske.krav.domain.ske.requests.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToLong


fun makeOpprettKravRequest(krav: KravLinje, uuid: String) = OpprettInnkrevingsoppdragRequest(
    kravtype = KravtypeMappingFromNAVToSKE.getKravtype(krav),
    skyldner = createSkyldner(krav),
    hovedstol = HovedstolBeloep(valuta = Valuta.NOK, beloep = krav.belop.toDouble().roundToLong()),
    renteBeloep = createRenteBelop(krav).takeIf { it.first().beloep > 0L },
    oppdragsgiversReferanse = krav.saksNummer,
    oppdragsgiversKravIdentifikator = uuid,
    fastsettelsesDato = krav.vedtakDato.toKotlinLocalDate(),
    tilleggsInformasjon = createTilleggsinformasjonNav(krav),
)


private fun createRenteBelop(krav: KravLinje): List<RenteBeloep> {
    val beloepRente = krav.belopRente.toDouble().roundToLong()
    return listOf(
        RenteBeloep(
            beloep = beloepRente,
            renterIlagtDato = krav.vedtakDato.toKotlinLocalDate(),
        ),
    )
}

private fun createSkyldner(krav: KravLinje) = if (krav.gjelderID.startsWith("00")) Skyldner(
    Skyldner.IdentifikatorType.ORGANISASJON,
    krav.gjelderID.substring(2, krav.gjelderID.length)
)
else Skyldner(Skyldner.IdentifikatorType.PERSON, krav.gjelderID)


private fun createTilleggsinformasjonNav(krav: KravLinje): TilleggsinformasjonNav {
    val kravFremtidigYtelse = krav.fremtidigYtelse.toDouble().roundToLong()
    val dtf = DateTimeFormatter.ofPattern("yyyyMMdd")
    val tilleggsinformasjonNav = TilleggsinformasjonNav(
        tilbakeKrevingsPeriode = TilbakeKrevingsPeriode(
            LocalDate.parse(krav.periodeFOM, dtf).toKotlinLocalDate(),
            LocalDate.parse(krav.periodeTOM, dtf).toKotlinLocalDate()
        ),
        ytelserForAvregning = YtelseForAvregningBeloep(beloep = kravFremtidigYtelse).takeIf { kravFremtidigYtelse > 0L },
    )
    return tilleggsinformasjonNav
}

fun makeEndreRenteRequest(krav: KravLinje) = EndreRenteBeloepRequest(
    createRenteBelop(krav)
)

fun makeEndreHovedstolRequest(krav: KravLinje): NyHovedStolRequest =
    NyHovedStolRequest(HovedstolBeloep(beloep = krav.belop.toDouble().roundToLong()))

fun makeNyOppdragsgiversReferanseRequest(krav: KravLinje) = NyOppdragsgiversReferanseRequest(krav.saksNummer)
fun makeStoppKravRequest(nyref: String, kravidentifikatortype: Kravidentifikatortype) =
    AvskrivingRequest(kravidentifikatortype.value, kravidentifikator = nyref)

fun KravLinje.isNyttKrav() = (!this.isEndring() && !this.isStopp())
fun KravLinje.isEndring() = (referanseNummerGammelSak.isNotEmpty() && !isStopp())
fun KravLinje.isStopp() = (belop.toDouble().roundToLong() == 0L)
