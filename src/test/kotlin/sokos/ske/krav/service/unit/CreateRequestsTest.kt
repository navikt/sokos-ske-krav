package sokos.ske.krav.service.unit

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.datetime.toKotlinLocalDate
import sokos.ske.krav.database.models.KravTable
import sokos.ske.krav.domain.nav.KravLinje
import sokos.ske.krav.domain.ske.requests.AvskrivingRequest
import sokos.ske.krav.domain.ske.requests.EndreRenteBeloepRequest
import sokos.ske.krav.domain.ske.requests.HovedstolBeloep
import sokos.ske.krav.domain.ske.requests.KravidentifikatorType
import sokos.ske.krav.domain.ske.requests.NyHovedStolRequest
import sokos.ske.krav.domain.ske.requests.RenteBeloep
import sokos.ske.krav.domain.ske.requests.Skyldner
import sokos.ske.krav.domain.ske.requests.Valuta
import sokos.ske.krav.domain.ske.requests.YtelseForAvregningBeloep
import sokos.ske.krav.util.createEndreHovedstolRequest
import sokos.ske.krav.util.createEndreRenteRequest
import sokos.ske.krav.util.createOpprettKravRequest
import sokos.ske.krav.util.createStoppKravRequest
import sokos.ske.krav.util.isEndring
import sokos.ske.krav.util.isOpprettKrav
import sokos.ske.krav.util.isStopp
import java.math.BigDecimal
import kotlin.math.roundToLong

internal class CreateRequestsTest :
    BehaviorSpec({

        Given("createOpprettKravRequest") {

            When("Skyldner er privatperson") {
                val kravTable = kravTableMockk(gjelderID = "01010122333")

                Then("Skal Skyldner settes til IdentifikatorType.PERSON") {
                    createOpprettKravRequest(kravTable).run {
                        skyldner.identifikatorType shouldBe Skyldner.IdentifikatorType.PERSON
                        skyldner.identifikator shouldBe kravTable.gjelderId
                    }
                }
            }
            When("Skyldner er organisasjon") {
                val kravTable = kravTableMockk(gjelderID = "00010122333")

                Then("Skal Skyldner settes til IdentifikatorType.ORGANISASJON") {
                    createOpprettKravRequest(kravTable).run {
                        skyldner.identifikatorType shouldBe Skyldner.IdentifikatorType.ORGANISASJON
                        skyldner.identifikator shouldBe "010122333"
                    }
                }
            }

            When("Rentebeløp er 0") {
                val kravTable = kravTableMockk(rentebelop = 0.0)

                Then("Skal rentebeløp settes til null") {
                    createOpprettKravRequest(kravTable).run {
                        renteBeloep shouldBe null
                    }
                }
            }
            When("Rentebeløp er over 0") {
                val kravTable = kravTableMockk(rentebelop = 10.0)

                Then("Skal rentebeløp settes til rentebeløp") {
                    createOpprettKravRequest(kravTable).run {
                        renteBeloep!!.first().beloep shouldBe kravTable.belopRente.roundToLong()
                    }
                }
            }

            When("Fremtidigytelse er over 0") {
                val kravTable = kravTableMockk(fremtidigytelse = 10.0)
                Then("Skal YtelseForAvregningBeloep settes til krav.fremtidigYtelse") {
                    createOpprettKravRequest(kravTable).run {
                        tilleggsInformasjon!!.ytelserForAvregning!!.beloep shouldBe kravTable.fremtidigYtelse.roundToLong()
                    }
                }
            }

            When("Fremtidigytelse er 0") {
                val kravTable = kravTableMockk(fremtidigytelse = 0.0)
                Then("Skal YtelseForAvregningBeloep være null") {
                    createOpprettKravRequest(kravTable).run {
                        tilleggsInformasjon?.ytelserForAvregning shouldBe null
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
                    foreldelsesFristensUtgangspunkt shouldBe kravTable.utbetalDato.toKotlinLocalDate()
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
        }

        Given("CreateEndreHovedstolRequest") {
            Then("Skal det dannes et NyHovedStolRequest") {
                val kravTable = kravTableMockk()
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

fun kravTableMockk(
    kravKode: String = "PE UT",
    kodehjemmel: String = "T",
    gjelderID: String = "01010122333",
    beloep: Double = 100.0,
    rentebelop: Double = 10.0,
    fremtidigytelse: Double = 10.0,
    periodeFom: String = "20010101",
    periodeTom: String = "20020202",
) = mockk<KravTable>(relaxed = true) {
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
