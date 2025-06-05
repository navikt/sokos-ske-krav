package no.nav.sokos.ske.krav.util

import java.time.LocalDate
import java.time.format.DateTimeFormatter

import kotlin.math.roundToLong
import kotlinx.datetime.toKotlinLocalDate

import no.nav.sokos.ske.krav.database.models.KravTable
import no.nav.sokos.ske.krav.domain.StonadsType
import no.nav.sokos.ske.krav.domain.nav.KravLinje
import no.nav.sokos.ske.krav.domain.ske.requests.AvskrivingRequest
import no.nav.sokos.ske.krav.domain.ske.requests.EndreRenteBeloepRequest
import no.nav.sokos.ske.krav.domain.ske.requests.HovedstolBeloep
import no.nav.sokos.ske.krav.domain.ske.requests.KravidentifikatorType
import no.nav.sokos.ske.krav.domain.ske.requests.NyHovedStolRequest
import no.nav.sokos.ske.krav.domain.ske.requests.OpprettInnkrevingsoppdragRequest
import no.nav.sokos.ske.krav.domain.ske.requests.RenteBeloep
import no.nav.sokos.ske.krav.domain.ske.requests.Skyldner
import no.nav.sokos.ske.krav.domain.ske.requests.TilbakeKrevingsPeriode
import no.nav.sokos.ske.krav.domain.ske.requests.TilleggsinformasjonNav
import no.nav.sokos.ske.krav.domain.ske.requests.Valuta
import no.nav.sokos.ske.krav.domain.ske.requests.YtelseForAvregningBeloep

fun createOpprettKravRequest(krav: KravTable) =
    OpprettInnkrevingsoppdragRequest(
        stonadstype = StonadsType.getStonadstype(krav.kravkode, krav.kodeHjemmel),
        skyldner = createSkyldner(krav),
        hovedstol = HovedstolBeloep(valuta = Valuta.NOK, beloep = krav.belop.roundToLong()),
        renteBeloep = createRenteBelop(krav).takeIf { it.first().beloep > 0L },
        oppdragsgiversReferanse = krav.fagsystemId,
        oppdragsgiversKravIdentifikator = krav.saksnummerNAV,
        fastsettelsesDato = krav.vedtaksDato.toKotlinLocalDate(),
        foreldelsesFristensUtgangspunkt = krav.utbetalDato.toKotlinLocalDate(),
        tilleggsInformasjon = createTilleggsinformasjonNav(krav),
    )

fun createEndreRenteRequest(krav: KravTable) =
    EndreRenteBeloepRequest(
        createRenteBelop(krav),
    )

fun createEndreHovedstolRequest(krav: KravTable): NyHovedStolRequest = NyHovedStolRequest(HovedstolBeloep(beloep = krav.belop.roundToLong()))

fun createStoppKravRequest(
    kravidentifikator: String,
    kravidentifikatorType: KravidentifikatorType,
) = AvskrivingRequest(kravidentifikatorType.value, kravidentifikator)

fun KravLinje.isOpprettKrav() = (!this.isEndring() && !this.isStopp())

fun KravLinje.isEndring() = (referansenummerGammelSak.isNotEmpty() && !isStopp())

fun KravLinje.isStopp() = (belop.toDouble().roundToLong() == 0L)

private fun createRenteBelop(krav: KravTable): List<RenteBeloep> =
    listOf(
        RenteBeloep(
            beloep = krav.belopRente.roundToLong(),
            renterIlagtDato = krav.vedtaksDato.toKotlinLocalDate(),
        ),
    )

private fun createSkyldner(krav: KravTable) =
    if (krav.gjelderId.startsWith("00")) {
        Skyldner(
            Skyldner.IdentifikatorType.ORGANISASJON,
            krav.gjelderId.substring(2, krav.gjelderId.length),
        )
    } else {
        Skyldner(Skyldner.IdentifikatorType.PERSON, krav.gjelderId)
    }

private fun createTilleggsinformasjonNav(krav: KravTable): TilleggsinformasjonNav {
    val kravFremtidigYtelse = krav.fremtidigYtelse.roundToLong()
    val dtf = DateTimeFormatter.ofPattern("yyyyMMdd")
    return TilleggsinformasjonNav(
        tilbakeKrevingsPeriode =
            TilbakeKrevingsPeriode(
                LocalDate.parse(krav.periodeFOM, dtf).toKotlinLocalDate(),
                LocalDate.parse(krav.periodeTOM, dtf).toKotlinLocalDate(),
            ),
        ytelserForAvregning =
            YtelseForAvregningBeloep(beloep = kravFremtidigYtelse)
                .takeIf { kravFremtidigYtelse > 0L },
    )
}
