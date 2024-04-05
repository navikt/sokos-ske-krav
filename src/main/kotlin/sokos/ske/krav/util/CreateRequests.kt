package sokos.ske.krav.util

import kotlinx.datetime.toKotlinLocalDate
import sokos.ske.krav.database.models.KravTable
import sokos.ske.krav.domain.Stonadstype
import sokos.ske.krav.domain.nav.KravLinje
import sokos.ske.krav.domain.ske.requests.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToLong


fun makeOpprettKravRequest(krav: KravTable) = OpprettInnkrevingsoppdragRequest(
    stonadstype = Stonadstype.getStonadstype(krav),
    skyldner = createSkyldner(krav),
    hovedstol = HovedstolBeloep(valuta = Valuta.NOK, beloep = krav.belop.roundToLong()),
    renteBeloep = createRenteBelop(krav).takeIf { it.first().beloep > 0L },
    oppdragsgiversReferanse = krav.fagsystemId,
    oppdragsgiversKravIdentifikator = krav.saksnummerNAV,
    fastsettelsesDato = krav.vedtakDato.toKotlinLocalDate(),
    foreldelsesFristensUtgangspunkt = krav.utbetalDato.toKotlinLocalDate(),
    tilleggsInformasjon = createTilleggsinformasjonNav(krav),
)

private fun createRenteBelop(krav: KravTable): List<RenteBeloep> = listOf(
    RenteBeloep(
        beloep = krav.belopRente.roundToLong(),
        renterIlagtDato = krav.vedtakDato.toKotlinLocalDate(),
    ),
)

private fun createSkyldner(krav: KravTable) =
    if (krav.gjelderId.startsWith("00")) Skyldner(
        Skyldner.IdentifikatorType.ORGANISASJON,
        krav.gjelderId.substring(2, krav.gjelderId.length))
    else Skyldner(Skyldner.IdentifikatorType.PERSON, krav.gjelderId)


private fun createTilleggsinformasjonNav(krav: KravTable): TilleggsinformasjonNav {
    val kravFremtidigYtelse = krav.fremtidigYtelse.roundToLong()
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

fun makeEndreRenteRequest(krav: KravTable) = EndreRenteBeloepRequest(
    createRenteBelop(krav)
)

fun makeEndreHovedstolRequest(krav: KravTable): NyHovedStolRequest =
    NyHovedStolRequest(HovedstolBeloep(beloep = krav.belop.roundToLong()))

fun makeStoppKravRequest(kravidentifikator: String, kravidentifikatorType: KravidentifikatorType) =
    AvskrivingRequest(kravidentifikatorType.value, kravidentifikator)

fun KravLinje.isOpprettKrav() = (!this.isEndring() && !this.isStopp())
fun KravLinje.isEndring() = (referanseNummerGammelSak.isNotEmpty() && !isStopp())
fun KravLinje.isStopp() = (belop.toDouble().roundToLong() == 0L)
