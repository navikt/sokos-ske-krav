package no.nav.sokos.ske.krav.util

import java.math.BigDecimal

import kotlin.math.roundToLong
import kotlinx.datetime.toKotlinLocalDate

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk

import no.nav.sokos.ske.krav.domain.Krav
import no.nav.sokos.ske.krav.dto.nav.KravLinje
import no.nav.sokos.ske.krav.dto.nav.isEndring
import no.nav.sokos.ske.krav.dto.nav.isOpprettKrav
import no.nav.sokos.ske.krav.dto.nav.isStopp
import no.nav.sokos.ske.krav.dto.ske.requests.AvskrivingRequest
import no.nav.sokos.ske.krav.dto.ske.requests.EndreRenteBeloepRequest
import no.nav.sokos.ske.krav.dto.ske.requests.HovedstolBeloep
import no.nav.sokos.ske.krav.dto.ske.requests.KravidentifikatorType
import no.nav.sokos.ske.krav.dto.ske.requests.NyHovedStolRequest
import no.nav.sokos.ske.krav.dto.ske.requests.RenteBeloep
import no.nav.sokos.ske.krav.dto.ske.requests.Skyldner
import no.nav.sokos.ske.krav.dto.ske.requests.Valuta
import no.nav.sokos.ske.krav.dto.ske.requests.YtelseForAvregningBeloep

internal class CreateRequestsTest :
    BehaviorSpec({

        Given("createOpprettKravRequest") {

            When("Skyldner er privatperson") {
                val kravTable = kravMock(gjelderID = "01010122333")

                Then("Skal Skyldner settes til IdentifikatorType.PERSON") {
                    createOpprettKravRequest(kravTable).run {
                        skyldner.identifikatorType shouldBe Skyldner.IdentifikatorType.PERSON
                        skyldner.identifikator shouldBe kravTable.gjelderId
                    }
                }
            }
            When("Skyldner er organisasjon") {
                val kravTable = kravMock(gjelderID = "00010122333")

                Then("Skal Skyldner settes til IdentifikatorType.ORGANISASJON") {
                    createOpprettKravRequest(kravTable).run {
                        skyldner.identifikatorType shouldBe Skyldner.IdentifikatorType.ORGANISASJON
                        skyldner.identifikator shouldBe "010122333"
                    }
                }
            }

            When("Rentebeløp er 0") {
                val kravTable = kravMock(rentebelop = 0.0)

                Then("Skal rentebeløp settes til null") {
                    createOpprettKravRequest(kravTable).run {
                        renteBeloep shouldBe null
                    }
                }
            }
            When("Rentebeløp er over 0") {
                val kravTable = kravMock(rentebelop = 10.0)

                Then("Skal rentebeløp settes til rentebeløp") {
                    createOpprettKravRequest(kravTable).run {
                        renteBeloep!!.first().beloep shouldBe kravTable.belopRente.roundToLong()
                    }
                }
            }

            When("Fremtidigytelse er over 0") {
                val kravTable = kravMock(fremtidigytelse = 10.0)
                Then("Skal YtelseForAvregningBeloep settes til krav.fremtidigYtelse") {
                    createOpprettKravRequest(kravTable).run {
                        tilleggsInformasjon!!.ytelserForAvregning!!.beloep shouldBe kravTable.fremtidigYtelse.roundToLong()
                    }
                }
            }

            When("Fremtidigytelse er 0") {
                val kravTable = kravMock(fremtidigytelse = 0.0)
                Then("Skal YtelseForAvregningBeloep være null") {
                    createOpprettKravRequest(kravTable).run {
                        tilleggsInformasjon?.ytelserForAvregning shouldBe null
                    }
                }
            }

            Then("Skal det dannes et OpprettKravRequest") {
                val kravTable = kravMock()
                createOpprettKravRequest(kravTable).run {
                    skyldner shouldBe Skyldner(Skyldner.IdentifikatorType.PERSON, kravTable.gjelderId)
                    hovedstol.beloep shouldBe kravTable.belop.roundToLong()
                    oppdragsgiversReferanse shouldBe kravTable.fagsystemId
                    oppdragsgiversKravIdentifikator shouldBe kravTable.saksnummerNAV
                    fastsettelsesDato shouldBe kravTable.vedtaksDato.toKotlinLocalDate()
                    foreldelsesFristensUtgangspunkt shouldBe kravTable.utbetalDato.toKotlinLocalDate()
                    tilleggsInformasjon!!.ytelserForAvregning shouldBe YtelseForAvregningBeloep(beloep = kravTable.fremtidigYtelse.roundToLong())
                }
            }
        }

        Given("createEndreRenteRequest") {
            Then("Skal det dannes et EndreRenteBeloepRequest") {
                val kravTable = kravMock()
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
        }

        Given("CreateEndreHovedstolRequest") {
            Then("Skal det dannes et NyHovedStolRequest") {
                val kravTable = kravMock()
                createEndreHovedstolRequest(kravTable) shouldBe NyHovedStolRequest(HovedstolBeloep(beloep = kravTable.belop.roundToLong()))
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
                val kravLinje = kravLinjeMockk()
                Then("Skal det returnere true") {
                    kravLinje.isOpprettKrav() shouldBe true
                }
            }
            When("KravLinje er endring krav") {
                val kravLinje = kravLinjeMockk(referansenummergammelsak = "123456789")
                Then("Skal det returnere false") {
                    kravLinje.isOpprettKrav() shouldBe false
                }
            }
            When("KravLinje er stopp krav") {
                val kravLinje = kravLinjeMockk(beloep = BigDecimal.ZERO)
                Then("Skal det returnere false") {
                    kravLinje.isOpprettKrav() shouldBe false
                }
            }
        }

        Given("isEndring") {
            When("KravLinje er endring krav") {
                val kravLinje = kravLinjeMockk(referansenummergammelsak = "123456789")
                Then("Skal det returnere true") {
                    kravLinje.isEndring() shouldBe true
                }
            }

            When("KravLinje er opprett krav") {
                val kravLinje = kravLinjeMockk()
                Then("Skal det returnere false") {
                    kravLinje.isEndring() shouldBe false
                }
            }

            When("KravLinje er stopp krav") {
                val kravLinje = kravLinjeMockk(beloep = BigDecimal.ZERO)
                Then("Skal det returnere false") {
                    kravLinje.isEndring() shouldBe false
                }
            }
        }

        Given("isStopp") {
            When("KravLinje er stopp krav") {
                val kravLinje = kravLinjeMockk(beloep = BigDecimal.ZERO)
                Then("Skal det returnere true") {
                    kravLinje.isStopp() shouldBe true
                }
            }

            When("KravLinje er opprett krav") {
                val kravLinje = kravLinjeMockk()
                Then("Skal det returnere false") {
                    kravLinje.isStopp() shouldBe false
                }
            }

            When("KravLinje er endring krav") {
                val kravLinje = kravLinjeMockk(referansenummergammelsak = "123456789")
                Then("Skal det returnere false") {
                    kravLinje.isStopp() shouldBe false
                }
            }
        }
    })

fun kravMock(
    kravKode: String = "PE UT",
    kodehjemmel: String = "T",
    gjelderID: String = "01010122333",
    beloep: Double = 100.0,
    rentebelop: Double = 10.0,
    fremtidigytelse: Double = 10.0,
    periodeFom: String = "20010101",
    periodeTom: String = "20020202",
) = mockk<Krav>(relaxed = true) {
    every { kravkode } returns kravKode
    every { kodeHjemmel } returns kodehjemmel
    every { gjelderId } returns gjelderID
    every { belop } returns beloep
    every { belopRente } returns rentebelop
    every { fremtidigYtelse } returns fremtidigytelse
    every { periodeFOM } returns periodeFom
    every { periodeTOM } returns periodeTom
}

fun kravLinjeMockk(
    referansenummergammelsak: String = "",
    beloep: BigDecimal = BigDecimal(100.0),
) = mockk<KravLinje>(relaxed = true) {
    every { referansenummerGammelSak } returns referansenummergammelsak
    every { belop } returns beloep
}
