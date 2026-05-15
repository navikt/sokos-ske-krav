package no.nav.sokos.ske.krav.service.unit

import java.math.BigDecimal
import java.time.LocalDate

import kotlin.math.roundToLong
import kotlinx.datetime.toKotlinLocalDate

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk

import no.nav.sokos.ske.krav.copybook.KravLinje
import no.nav.sokos.ske.krav.domain.Krav
import no.nav.sokos.ske.krav.dto.ske.requests.AvskrivingRequest
import no.nav.sokos.ske.krav.dto.ske.requests.EndreRenteBeloepRequest
import no.nav.sokos.ske.krav.dto.ske.requests.HovedstolBeloep
import no.nav.sokos.ske.krav.dto.ske.requests.KravidentifikatorType
import no.nav.sokos.ske.krav.dto.ske.requests.NyHovedStolRequest
import no.nav.sokos.ske.krav.dto.ske.requests.RenteBeloep
import no.nav.sokos.ske.krav.dto.ske.requests.Skyldner
import no.nav.sokos.ske.krav.dto.ske.requests.TilbakeKrevingsPeriode
import no.nav.sokos.ske.krav.dto.ske.requests.Valuta
import no.nav.sokos.ske.krav.dto.ske.requests.YtelseForAvregningBeloep
import no.nav.sokos.ske.krav.util.createEndreHovedstolRequest
import no.nav.sokos.ske.krav.util.createEndreRenteRequest
import no.nav.sokos.ske.krav.util.createOpprettKravRequest
import no.nav.sokos.ske.krav.util.createStoppKravRequest
import no.nav.sokos.ske.krav.validation.LineValidationRules

internal class CreateRequestsTest :
    BehaviorSpec({

        Given("createOpprettKravRequest") {

            When("Skyldner er privatperson") {
                val kravTable = kravTableMockk(gjelderIdParam = "01010122333")

                Then("Skal Skyldner settes til IdentifikatorType.PERSON") {
                    createOpprettKravRequest(kravTable).run {
                        skyldner.identifikatorType shouldBe Skyldner.IdentifikatorType.PERSON
                        skyldner.identifikator shouldBe kravTable.gjelderId
                    }
                }
            }
            When("Skyldner er organisasjon") {
                val kravTable = kravTableMockk(gjelderIdParam = "00010122333")

                Then("Skal Skyldner settes til IdentifikatorType.ORGANISASJON") {
                    createOpprettKravRequest(kravTable).run {
                        skyldner.identifikatorType shouldBe Skyldner.IdentifikatorType.ORGANISASJON
                        skyldner.identifikator shouldBe "010122333"
                    }
                }
            }

            When("Skyldner er organisasjon med 9 siffere etter 00") {
                val kravTable = kravTableMockk(gjelderIdParam = "00123456789")

                Then("Skal Skyldner fjerne de to første 00") {
                    createOpprettKravRequest(kravTable).run {
                        skyldner.identifikatorType shouldBe Skyldner.IdentifikatorType.ORGANISASJON
                        skyldner.identifikator shouldBe "123456789"
                    }
                }
            }

            When("Rentebeløp er 0") {
                val kravTable = kravTableMockk(belopRenteParam = 0.0)

                Then("Skal rentebeløp settes til null") {
                    createOpprettKravRequest(kravTable).run {
                        renteBeloep shouldBe null
                    }
                }
            }
            When("Rentebeløp er over 0") {
                val kravTable = kravTableMockk(belopRenteParam = 10.0)

                Then("Skal rentebeløp settes til rentebeløp") {
                    createOpprettKravRequest(kravTable).run {
                        renteBeloep!!.first().beloep shouldBe kravTable.belopRente.roundToLong()
                    }
                }
            }

            When("Fremtidigytelse er over 0") {
                val kravTable = kravTableMockk(fremtidigYtelseParam = 10.0)
                Then("Skal YtelseForAvregningBeloep settes til krav.fremtidigYtelse") {
                    createOpprettKravRequest(kravTable).run {
                        tilleggsInformasjon!!.ytelserForAvregning!!.beloep shouldBe kravTable.fremtidigYtelse.roundToLong()
                    }
                }
            }

            When("Fremtidigytelse er 0") {
                val kravTable = kravTableMockk(fremtidigYtelseParam = 0.0)
                Then("Skal YtelseForAvregningBeloep være null") {
                    createOpprettKravRequest(kravTable).run {
                        tilleggsInformasjon?.ytelserForAvregning shouldBe null
                    }
                }
            }

            When("Fremtidigytelse er negativ") {
                val kravTable = kravTableMockk(fremtidigYtelseParam = -10.0)
                Then("Skal YtelseForAvregningBeloep være null") {
                    createOpprettKravRequest(kravTable).run {
                        tilleggsInformasjon?.ytelserForAvregning shouldBe null
                    }
                }
            }

            When("tilleggsfrist er satt") {
                val tilleggsfristDato = LocalDate.of(2023, 6, 15)
                val kravTable = kravTableMockk(tilleggsfristParam = tilleggsfristDato)
                Then("Skal foreldelsesFristensUtgangspunkt være null og tilleggsfrist settes") {
                    createOpprettKravRequest(kravTable).run {
                        foreldelsesFristensUtgangspunkt shouldBe null
                        tilleggsfrist shouldBe tilleggsfristDato.toKotlinLocalDate()
                    }
                }
            }

            When("tilleggsfrist er null og utbetalDato er errorDate") {
                val kravTable =
                    kravTableMockk(
                        tilleggsfristParam = null,
                        utbetalDatoParam = LineValidationRules.errorDate,
                    )
                Then("Skal foreldelsesFristensUtgangspunkt være null") {
                    createOpprettKravRequest(kravTable).run {
                        foreldelsesFristensUtgangspunkt shouldBe null
                        tilleggsfrist shouldBe null
                    }
                }
            }

            When("tilleggsfrist er null og utbetalDato er lik vedtaksDato") {
                val vedtaksDato = LocalDate.of(2022, 5, 10)
                val kravTable =
                    kravTableMockk(
                        tilleggsfristParam = null,
                        vedtaksDatoParam = vedtaksDato,
                        utbetalDatoParam = vedtaksDato,
                    )
                Then("Skal foreldelsesFristensUtgangspunkt være null") {
                    createOpprettKravRequest(kravTable).run {
                        foreldelsesFristensUtgangspunkt shouldBe null
                        tilleggsfrist shouldBe null
                    }
                }
            }

            When("tilleggsfrist er null og utbetalDato er etter vedtaksDato") {
                val vedtaksDato = LocalDate.of(2022, 5, 10)
                val utbetalDato = LocalDate.of(2022, 6, 15)
                val kravTable =
                    kravTableMockk(
                        tilleggsfristParam = null,
                        vedtaksDatoParam = vedtaksDato,
                        utbetalDatoParam = utbetalDato,
                    )
                Then("Skal foreldelsesFristensUtgangspunkt være null") {
                    createOpprettKravRequest(kravTable).run {
                        foreldelsesFristensUtgangspunkt shouldBe null
                        tilleggsfrist shouldBe null
                    }
                }
            }

            When("tilleggsfrist er null og utbetalDato er før vedtaksDato") {
                val vedtaksDato = LocalDate.of(2022, 5, 10)
                val utbetalDato = LocalDate.of(2022, 3, 15)
                val kravTable =
                    kravTableMockk(
                        tilleggsfristParam = null,
                        vedtaksDatoParam = vedtaksDato,
                        utbetalDatoParam = utbetalDato,
                    )
                Then("Skal foreldelsesFristensUtgangspunkt være utbetalDato") {
                    createOpprettKravRequest(kravTable).run {
                        foreldelsesFristensUtgangspunkt shouldBe utbetalDato.toKotlinLocalDate()
                        tilleggsfrist shouldBe null
                    }
                }
            }

            When("Beløp har desimaler") {
                val kravTable = kravTableMockk(belopParam = 100.75)
                Then("Skal beløpet rundes til nærmeste heltall") {
                    createOpprettKravRequest(kravTable).run {
                        hovedstol.beloep shouldBe 101L
                    }
                }
            }

            When("fagsystemId er blank") {
                val kravTable = kravTableMockk(fagsystemIdParam = "")
                Then("Skal oppdragsgiversReferanse settes til null") {
                    createOpprettKravRequest(kravTable).run {
                        oppdragsgiversReferanse shouldBe null
                    }
                }
            }

            When("fagsystemId er ikke blank") {
                val kravTable = kravTableMockk(fagsystemIdParam = "TEST-123")
                Then("Skal oppdragsgiversReferanse settes til fagsystemId") {
                    createOpprettKravRequest(kravTable).run {
                        oppdragsgiversReferanse shouldBe "TEST-123"
                    }
                }
            }

            When("TilbakeKrevingsPeriode har datoer") {
                val kravTable =
                    kravTableMockk(
                        periodeFomParam = "20220115",
                        periodeTomParam = "20220630",
                    )
                Then("Skal tilbakeKrevingsPeriode inneholde riktige datoer") {
                    createOpprettKravRequest(kravTable).run {
                        tilleggsInformasjon!!.tilbakeKrevingsPeriode shouldBe
                            TilbakeKrevingsPeriode(
                                fraDato = LocalDate.of(2022, 1, 15).toKotlinLocalDate(),
                                tilDato = LocalDate.of(2022, 6, 30).toKotlinLocalDate(),
                            )
                    }
                }
            }

            Then("Skal det dannes et OpprettKravRequest") {
                val kravTable = kravTableMockk()
                createOpprettKravRequest(kravTable).run {
                    skyldner shouldBe Skyldner(Skyldner.IdentifikatorType.PERSON, kravTable.gjelderId)
                    hovedstol.beloep shouldBe kravTable.belop.roundToLong()
                    oppdragsgiversReferanse shouldBe kravTable.fagsystemId
                    oppdragsgiversKravIdentifikator shouldBe kravTable.saksnummerNAV
                    fastsettelsesDato shouldBe kravTable.vedtaksDato.toKotlinLocalDate()

                    if (kravTable.tilleggsfrist != null) {
                        foreldelsesFristensUtgangspunkt shouldBe null
                        tilleggsfrist shouldBe kravTable.tilleggsfrist.toKotlinLocalDate()
                    } else {
                        foreldelsesFristensUtgangspunkt shouldBe kravTable.utbetalDato.toKotlinLocalDate()
                        tilleggsfrist shouldBe null
                    }

                    tilleggsInformasjon!!.ytelserForAvregning shouldBe YtelseForAvregningBeloep(beloep = kravTable.fremtidigYtelse.roundToLong())
                }
            }
        }

        Given("createEndreRenteRequest") {
            Then("Skal det dannes et EndreRenteBeloepRequest") {
                val kravTable = kravTableMockk()
                createEndreRenteRequest(kravTable) shouldBe
                    EndreRenteBeloepRequest(
                        listOf(
                            RenteBeloep(
                                beloep = kravTable.belopRente.roundToLong(),
                                renterIlagtDato = kravTable.vedtaksDato.toKotlinLocalDate(),
                                valuta = Valuta.NOK,
                                rentetype = "STRAFFERENTE",
                            ),
                        ),
                    )
            }

            When("Rentebeløp har desimaler") {
                val kravTable = kravTableMockk(belopRenteParam = 15.49)
                Then("Skal rentebeløpet rundes til nærmeste heltall") {
                    createEndreRenteRequest(kravTable).renter.first().beloep shouldBe 15L
                }
            }

            When("Rentebeløp har desimaler som rundes opp") {
                val kravTable = kravTableMockk(belopRenteParam = 15.50)
                Then("Skal rentebeløpet rundes opp") {
                    createEndreRenteRequest(kravTable).renter.first().beloep shouldBe 16L
                }
            }
        }

        Given("CreateEndreHovedstolRequest") {
            Then("Skal det dannes et NyHovedStolRequest") {
                val kravTable = kravTableMockk()
                createEndreHovedstolRequest(kravTable) shouldBe NyHovedStolRequest(HovedstolBeloep(beloep = kravTable.belop.roundToLong()))
            }

            When("Hovedstol har desimaler") {
                val kravTable = kravTableMockk(belopParam = 999.49)
                Then("Skal hovedstolen rundes til nærmeste heltall") {
                    createEndreHovedstolRequest(kravTable).hovedstol.beloep shouldBe 999L
                }
            }

            When("Hovedstol har desimaler som rundes opp") {
                val kravTable = kravTableMockk(belopParam = 999.50)
                Then("Skal hovedstolen rundes opp") {
                    createEndreHovedstolRequest(kravTable).hovedstol.beloep shouldBe 1000L
                }
            }
        }

        Given("createStoppKravRequest") {
            Then("Skal det dannes et AvskrivingRequest") {
                val kravidentifikator = "123456789"
                val kravidentifikatorType = KravidentifikatorType.OPPDRAGSGIVERSKRAVIDENTIFIKATOR
                createStoppKravRequest(kravidentifikator, kravidentifikatorType) shouldBe AvskrivingRequest(kravidentifikatorType.value, kravidentifikator)
            }
        }

        Given("isOpprettKrav") {
            When("KravLinje er opprett krav") {
                val kravLinje = kravLinje()
                Then("Skal det returnere true") {
                    kravLinje.isOpprettKrav() shouldBe true
                }
            }
            When("KravLinje er endring krav") {
                val kravLinje = kravLinje(referansenummerGammelSakParam = "123456789")
                Then("Skal det returnere false") {
                    kravLinje.isOpprettKrav() shouldBe false
                }
            }
            When("KravLinje er stopp krav") {
                val kravLinje = kravLinje(belopParam = BigDecimal.ZERO)
                Then("Skal det returnere false") {
                    kravLinje.isOpprettKrav() shouldBe false
                }
            }
        }

        Given("isEndring") {
            When("KravLinje er endring krav") {
                val kravLinje = kravLinje(referansenummerGammelSakParam = "123456789")
                Then("Skal det returnere true") {
                    kravLinje.isEndring() shouldBe true
                }
            }

            When("KravLinje er opprett krav") {
                val kravLinje = kravLinje()
                Then("Skal det returnere false") {
                    kravLinje.isEndring() shouldBe false
                }
            }

            When("KravLinje er stopp krav") {
                val kravLinje = kravLinje(belopParam = BigDecimal.ZERO)
                Then("Skal det returnere false") {
                    kravLinje.isEndring() shouldBe false
                }
            }
        }

        Given("isStopp") {
            When("KravLinje er stopp krav") {
                val kravLinje = kravLinje(belopParam = BigDecimal.ZERO)
                Then("Skal det returnere true") {
                    kravLinje.isStopp() shouldBe true
                }
            }

            When("KravLinje har veldig lite beløp som rundes til 0") {
                val kravLinje = kravLinje(belopParam = BigDecimal("0.49"))
                Then("Skal det returnere true") {
                    kravLinje.isStopp() shouldBe true
                }
            }

            When("KravLinje har lite beløp som rundes til 1") {
                val kravLinje = kravLinje(belopParam = BigDecimal("0.50"))
                Then("Skal det returnere false") {
                    kravLinje.isStopp() shouldBe false
                }
            }

            When("KravLinje er opprett krav") {
                val kravLinje = kravLinje()
                Then("Skal det returnere false") {
                    kravLinje.isStopp() shouldBe false
                }
            }

            When("KravLinje er endring krav") {
                val kravLinje = kravLinje(referansenummerGammelSakParam = "123456789")
                Then("Skal det returnere false") {
                    kravLinje.isStopp() shouldBe false
                }
            }
        }
    })

fun kravTableMockk(
    kravkodeParam: String = "PE UT",
    kodeHjemmelParam: String = "T",
    gjelderIdParam: String = "01010122333",
    belopParam: Double = 100.0,
    belopRenteParam: Double = 10.0,
    fremtidigYtelseParam: Double = 10.0,
    periodeFomParam: String = "20010101",
    periodeTomParam: String = "20020202",
    vedtaksDatoParam: LocalDate = LocalDate.of(2022, 5, 10),
    utbetalDatoParam: LocalDate = LocalDate.of(2022, 3, 1),
    tilleggsfristParam: LocalDate? = null,
    fagsystemIdParam: String = "TEST-123",
    saksnummerNavParam: String = "SAK-456",
) = mockk<Krav>(relaxed = true) {
    every { kravkode } returns kravkodeParam
    every { kodeHjemmel } returns kodeHjemmelParam
    every { gjelderId } returns gjelderIdParam
    every { belop } returns belopParam
    every { belopRente } returns belopRenteParam
    every { fremtidigYtelse } returns fremtidigYtelseParam
    every { periodeFOM } returns periodeFomParam
    every { periodeTOM } returns periodeTomParam
    every { vedtaksDato } returns vedtaksDatoParam
    every { utbetalDato } returns utbetalDatoParam
    every { tilleggsfrist } returns tilleggsfristParam
    every { fagsystemId } returns fagsystemIdParam
    every { saksnummerNAV } returns saksnummerNavParam
}

fun kravLinje(
    referansenummerGammelSakParam: String = "",
    belopParam: BigDecimal = BigDecimal(100.0),
) = KravLinje(
    linjenummer = 1,
    saksnummerNav = "",
    belop = belopParam,
    vedtaksDato = LocalDate.now(),
    gjelderId = "",
    periodeFOM = "",
    periodeTOM = "",
    kravKode = "",
    referansenummerGammelSak = referansenummerGammelSakParam,
    transaksjonsDato = "",
    enhetBosted = "",
    enhetBehandlende = "",
    kodeHjemmel = "",
    kodeArsak = "",
    belopRente = BigDecimal.ZERO,
    fremtidigYtelse = BigDecimal.ZERO,
    utbetalDato = LocalDate.now(),
    fagsystemId = "",
    avsender = "",
)
