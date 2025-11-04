package no.nav.sokos.ske.krav.service.unit

import java.time.LocalDate
import java.time.format.DateTimeFormatter

import kotlin.math.roundToLong
import kotlinx.datetime.toKotlinLocalDate

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.mockk.coEvery
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify

import no.nav.sokos.ske.krav.client.SkeClient
import no.nav.sokos.ske.krav.domain.Krav
import no.nav.sokos.ske.krav.domain.Status
import no.nav.sokos.ske.krav.domain.StonadsType
import no.nav.sokos.ske.krav.dto.ske.requests.HovedstolBeloep
import no.nav.sokos.ske.krav.dto.ske.requests.OpprettInnkrevingsoppdragRequest
import no.nav.sokos.ske.krav.dto.ske.requests.RenteBeloep
import no.nav.sokos.ske.krav.dto.ske.requests.Skyldner
import no.nav.sokos.ske.krav.dto.ske.requests.TilbakeKrevingsPeriode
import no.nav.sokos.ske.krav.dto.ske.requests.TilleggsinformasjonNav
import no.nav.sokos.ske.krav.dto.ske.requests.Valuta
import no.nav.sokos.ske.krav.dto.ske.requests.YtelseForAvregningBeloep
import no.nav.sokos.ske.krav.dto.ske.responses.OpprettInnkrevingsOppdragResponse
import no.nav.sokos.ske.krav.service.DatabaseService
import no.nav.sokos.ske.krav.service.OpprettKravService
import no.nav.sokos.ske.krav.util.RequestResult
import no.nav.sokos.ske.krav.util.encodeToString

class OpprettKravServiceTest :
    FunSpec({
        val databaseServiceMock =
            mockk<DatabaseService> {
                justRun { updateSentKrav(any<List<RequestResult>>()) }
            }
        val kravMock =
            mockk<Krav>(relaxed = true) {
                every { kravidentifikatorSKE } returns "foo"
                every { saksnummerNAV } returns "bar"
                every { gjelderId } returns "12131456789"
                every { kravkode } returns "BA OR"
                every { kodeHjemmel } returns "T"
                every { belop } returns 100.0
                every { belopRente } returns 10.0
                every { vedtaksDato } returns LocalDate.now().plusDays(30)
                every { utbetalDato } returns LocalDate.now()
                every { fagsystemId } returns "123"
                every { periodeFOM } returns "20210101"
                every { periodeTOM } returns "20210102"
                every { fremtidigYtelse } returns 10.0
                every { tilleggsfrist } returns null
            }

        test("sendAllOpprettKrav skal returnere liste av innsendte nye krav") {
            val opprettKravServiceMock =
                spyk(OpprettKravService(mockk<SkeClient>(), databaseServiceMock))
            every { opprettKravServiceMock["sendAllOpprettKrav"](any<List<Krav>>()) } returns
                listOf(
                    RequestResult(mockk<HttpResponse>(relaxed = true), mockk<Krav>(), "", "123", mockk<Status>(relaxed = true)),
                    RequestResult(mockk<HttpResponse>(relaxed = true), mockk<Krav>(), "", "456", mockk<Status>(relaxed = true)),
                )

            val result = opprettKravServiceMock.sendAllOpprettKrav(listOf(kravMock, kravMock))

            result.size shouldBe 2
            result.filter { it.kravidentifikator == "123" }.size shouldBe 1
            result.filter { it.kravidentifikator == "456" }.size shouldBe 1
        }

        test("sendOpprettKrav skal returnere RequestResult") {
            val opprettInnkrevingOppdragRequest =
                OpprettInnkrevingsoppdragRequest(
                    stonadstype = StonadsType.TILBAKEKREVING_BARNETRYGD,
                    skyldner = Skyldner(Skyldner.IdentifikatorType.PERSON, kravMock.gjelderId),
                    hovedstol = HovedstolBeloep(valuta = Valuta.NOK, beloep = kravMock.belop.roundToLong()),
                    renteBeloep = listOf(RenteBeloep(beloep = kravMock.belopRente.roundToLong(), renterIlagtDato = kravMock.vedtaksDato.toKotlinLocalDate())),
                    oppdragsgiversReferanse = kravMock.fagsystemId,
                    oppdragsgiversKravIdentifikator = kravMock.saksnummerNAV,
                    fastsettelsesDato = kravMock.vedtaksDato.toKotlinLocalDate(),
                    foreldelsesFristensUtgangspunkt = kravMock.utbetalDato.toKotlinLocalDate(),
                    tilleggsInformasjon =
                        TilleggsinformasjonNav(
                            ytelserForAvregning = YtelseForAvregningBeloep(beloep = kravMock.fremtidigYtelse.roundToLong()),
                            tilbakeKrevingsPeriode =
                                TilbakeKrevingsPeriode(
                                    LocalDate.parse(kravMock.periodeFOM, DateTimeFormatter.ofPattern("yyyyMMdd")).toKotlinLocalDate(),
                                    LocalDate.parse(kravMock.periodeTOM, DateTimeFormatter.ofPattern("yyyyMMdd")).toKotlinLocalDate(),
                                ),
                        ),
                )
            val httpResponseMock =
                mockk<HttpResponse>(relaxed = true) {
                    every { status.value } returns 200
                    coEvery { body<OpprettInnkrevingsOppdragResponse>() } returns
                        OpprettInnkrevingsOppdragResponse(
                            kravidentifikator = "123",
                        )
                }
            val skeClientMock = mockk<SkeClient> { coEvery { opprettKrav(any(), any()) } returns httpResponseMock }
            val opprettKravServiceMock = spyk(OpprettKravService(skeClientMock, databaseServiceMock), recordPrivateCalls = true)

            val reqResult = opprettKravServiceMock.sendAllOpprettKrav(listOf(kravMock))
            verify(exactly = 1) {
                opprettKravServiceMock["sendOpprettKrav"](any<Krav>())
            }

            reqResult.size shouldBe 1
            with(reqResult.first()) {
                response shouldBe httpResponseMock
                request shouldBe opprettInnkrevingOppdragRequest.encodeToString()
                krav shouldBe kravMock
                kravidentifikator shouldBe "123"
                status shouldBe Status.KRAV_SENDT
            }
        }

        test("sendOpprettKrav skal returnere RequestResult når tilleggsfrist er satt") {
            val kravMockMedTilleggsfrist =
                mockk<Krav>(relaxed = true) {
                    every { kravidentifikatorSKE } returns "foo"
                    every { saksnummerNAV } returns "bar"
                    every { gjelderId } returns "12131456789"
                    every { kravkode } returns "BA OR"
                    every { kodeHjemmel } returns "T"
                    every { belop } returns 100.0
                    every { belopRente } returns 10.0
                    every { vedtaksDato } returns LocalDate.now()
                    every { utbetalDato } returns LocalDate.now().plusDays(5)
                    every { fagsystemId } returns "123"
                    every { periodeFOM } returns "20210101"
                    every { periodeTOM } returns "20210102"
                    every { fremtidigYtelse } returns 10.0
                    every { tilleggsfrist } returns LocalDate.now().plusYears(3)
                }

            val opprettInnkrevingOppdragRequest =
                OpprettInnkrevingsoppdragRequest(
                    stonadstype = StonadsType.TILBAKEKREVING_BARNETRYGD,
                    skyldner = Skyldner(Skyldner.IdentifikatorType.PERSON, kravMockMedTilleggsfrist.gjelderId),
                    hovedstol = HovedstolBeloep(valuta = Valuta.NOK, beloep = kravMockMedTilleggsfrist.belop.roundToLong()),
                    renteBeloep = listOf(RenteBeloep(beloep = kravMockMedTilleggsfrist.belopRente.roundToLong(), renterIlagtDato = kravMockMedTilleggsfrist.vedtaksDato.toKotlinLocalDate())),
                    oppdragsgiversReferanse = kravMockMedTilleggsfrist.fagsystemId,
                    oppdragsgiversKravIdentifikator = kravMockMedTilleggsfrist.saksnummerNAV,
                    fastsettelsesDato = kravMockMedTilleggsfrist.vedtaksDato.toKotlinLocalDate(),
                    foreldelsesFristensUtgangspunkt = null,
                    tilleggsfrist = kravMockMedTilleggsfrist.tilleggsfrist?.toKotlinLocalDate(),
                    tilleggsInformasjon =
                        TilleggsinformasjonNav(
                            ytelserForAvregning = YtelseForAvregningBeloep(beloep = kravMockMedTilleggsfrist.fremtidigYtelse.roundToLong()),
                            tilbakeKrevingsPeriode =
                                TilbakeKrevingsPeriode(
                                    LocalDate.parse(kravMockMedTilleggsfrist.periodeFOM, DateTimeFormatter.ofPattern("yyyyMMdd")).toKotlinLocalDate(),
                                    LocalDate.parse(kravMockMedTilleggsfrist.periodeTOM, DateTimeFormatter.ofPattern("yyyyMMdd")).toKotlinLocalDate(),
                                ),
                        ),
                )

            val httpResponseMock =
                mockk<HttpResponse>(relaxed = true) {
                    every { status.value } returns 200
                    coEvery { body<OpprettInnkrevingsOppdragResponse>() } returns
                        OpprettInnkrevingsOppdragResponse(
                            kravidentifikator = "123",
                        )
                }
            val skeClientMock = mockk<SkeClient> { coEvery { opprettKrav(any(), any()) } returns httpResponseMock }
            val opprettKravServiceMock = spyk(OpprettKravService(skeClientMock, databaseServiceMock), recordPrivateCalls = true)

            val reqResult = opprettKravServiceMock.sendAllOpprettKrav(listOf(kravMockMedTilleggsfrist))

            reqResult.size shouldBe 1
            with(reqResult.first()) {
                response shouldBe httpResponseMock
                request shouldBe opprettInnkrevingOppdragRequest.encodeToString()
                krav shouldBe kravMockMedTilleggsfrist
                kravidentifikator shouldBe "123"
                status shouldBe Status.KRAV_SENDT
            }
        }
    })
