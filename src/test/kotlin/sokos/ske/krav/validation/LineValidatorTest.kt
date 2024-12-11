package sokos.ske.krav.validation

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import sokos.ske.krav.client.SlackClient
import sokos.ske.krav.domain.Status
import sokos.ske.krav.domain.nav.KravLinje
import sokos.ske.krav.service.DatabaseService
import sokos.ske.krav.service.FtpFil
import sokos.ske.krav.util.MockHttpClient
import java.math.BigDecimal
import java.time.LocalDate

internal class LineValidatorTest :
    BehaviorSpec({
        val dsMock = mockk<DatabaseService>(relaxed = true)
        val lineValidator = LineValidator(SlackClient(client = MockHttpClient().getSlackClient()))

        fun ftpFile(
            name: String,
            kravLinjer: List<KravLinje>,
        ) = FtpFil(name, emptyList(), kravLinjer)

        suspend fun errorValidation(
            fil: FtpFil,
            numberOfAddedFailureLines: Int = 1,
        ) {
            val validatedLines = lineValidator.validateNewLines(fil, dsMock)
            validatedLines.size shouldBe fil.kravLinjer.size
            validatedLines.filter { it.status == Status.KRAV_IKKE_SENDT.value }.size shouldBe fil.kravLinjer.size - numberOfAddedFailureLines
            validatedLines.last().status shouldBe "VALIDERINGSFEIL_AV_LINJE_I_FIL"
        }

        Given("Alle linjer er ok") {
            val fil = ftpFile(this.testCase.name.testName, getKravlinjer())

            Then("Skal validering returnere ValidationResult Success") {
                val validatedLines = lineValidator.validateNewLines(fil, dsMock)
                validatedLines.size shouldBe 5
                validatedLines.filter { it.status == Status.KRAV_IKKE_SENDT.value }.size shouldBe 5
            }
        }

        Given("Kravtype er ugyldig") {
            val fil =
                ftpFile(
                    this.testCase.name.testName,
                    getKravlinjer().apply {
                        add(get(0).copy(linjenummer = 6, kravKode = "MJ AU"))
                    },
                )

            Then("Skal validering returnere ValidationResult Error") {
                errorValidation(fil)
            }
        }

        Given("Saksnummer er ikke riktig formatert") {
            val fil =
                ftpFile(
                    this.testCase.name.testName,
                    getKravlinjer().apply {
                        add(get(0).copy(linjenummer = 6, saksnummerNav = "saksnummer_ø"))
                    },
                )

            Then("Skal validering returnere ValidationResult Error") {
                errorValidation(fil)
            }
        }

        Given("Refnummer gamme sak er ikke riktig formatert") {
            val fil =
                ftpFile(
                    this.testCase.name.testName,
                    getKravlinjer().apply {
                        add(get(0).copy(linjenummer = 6, referansenummerGammelSak = "refgammelsak_ø"))
                    },
                )

            Then("Skal validering returnere ValidationResult Error") {
                errorValidation(fil)
            }
        }

        Given("Vedtaksdato er i fremtiden") {
            val fil =
                ftpFile(
                    this.testCase.name.testName,
                    getKravlinjer().apply {
                        add(get(0).copy(linjenummer = 6, vedtaksDato = LocalDate.now().plusDays(1)))
                    },
                )

            Then("Skal validering returnere ValidationResult Error") {
                errorValidation(fil)
            }
        }

        Given("Periode er ikke i fortid") {
            val fomIFremtid =
                LocalDate
                    .now()
                    .plusDays(3)
                    .toString()
                    .replace("-", "")
            val tomIFremtid =
                LocalDate
                    .now()
                    .plusDays(5)
                    .toString()
                    .replace("-", "")
            val nyLinje = getKravlinjer()[0].copy(periodeFOM = fomIFremtid, periodeTOM = tomIFremtid)

            When("Kravkode er FO FT") {
                val fil =
                    ftpFile(
                        this.testCase.name.testName,
                        getKravlinjer().apply {
                            add(nyLinje.copy(kravKode = "FO FT", kodeHjemmel = "FT"))
                        },
                    )
                Then("Skal validering returnere ValidationResult Success") {
                    val validatedLines = lineValidator.validateNewLines(fil, dsMock)
                    validatedLines.size shouldBe 6
                    validatedLines.filter { it.status == Status.KRAV_IKKE_SENDT.value }.size shouldBe 6
                }
            }
            When("Kravode ikke er FO FT") {
                val fil =
                    ftpFile(
                        this.testCase.name.testName,
                        getKravlinjer().apply {
                            add(nyLinje.copy(kravKode = "BA OR"))
                        },
                    )
                Then("Skal validering returnere ValidationResult Error") {
                    errorValidation(fil)
                }
            }
        }

        Given("TOM er før FOM i periode") {
            val fil =
                ftpFile(
                    this.testCase.name.testName,
                    getKravlinjer().apply {
                        add(get(0).copy(periodeFOM = "20241211", periodeTOM = "20241210"))
                    },
                )

            Then("Skal validering returnere ValidationResult Error") {
                errorValidation(fil)
            }
        }

        Given("Utbetalingsdato er lik vedtaksdato") {
            val fil =
                ftpFile(
                    this.testCase.name.testName,
                    getKravlinjer().apply {
                        add(get(0).copy(linjenummer = 6, utbetalDato = LocalDate.now(), vedtaksDato = LocalDate.now()))
                    },
                )
            Then("Skal validering returnere ValidationResult Error") {
                errorValidation(fil)
            }
        }

        Given("Utbetalingsdato er ikke i fortid") {
            val fil =
                ftpFile(
                    this.testCase.name.testName,
                    getKravlinjer().apply {
                        add(get(0).copy(linjenummer = 6, utbetalDato = LocalDate.now(), vedtaksDato = LocalDate.now()))
                    },
                )

            Then("Skal validering returnere ValidationResult Error") {
                errorValidation(fil)
            }
        }

        Given("Datoer er feil formattert") {
            When("Utbetalingsdato er feil formattert") {
                val fil =
                    ftpFile(
                        this.testCase.name.testName,
                        getKravlinjer().apply {
                            add(get(0).copy(linjenummer = 6, utbetalDato = LineValidationRules.errorDate))
                        },
                    )
                Then("Skal validering returnere ValidationResult Error") {
                    errorValidation(fil)
                }
            }
            When("Vedtaksdato er feil formattert") {
                val fil =
                    ftpFile(
                        this.testCase.name.testName,
                        getKravlinjer().apply {
                            add(get(0).copy(linjenummer = 6, vedtaksDato = LineValidationRules.errorDate))
                        },
                    )
                Then("Skal validering returnere ValidationResult Error") {
                    errorValidation(fil)
                }
            }

            When("Periode fom er feil formattert") {
                val fil =
                    ftpFile(
                        this.testCase.name.testName,
                        getKravlinjer().apply {
                            add(get(0).copy(linjenummer = 6, periodeFOM = LineValidationRules.errorDate.toString()))
                        },
                    )
                Then("Skal validering returnere ValidationResult Error") {
                    errorValidation(fil)
                }
            }
            When("Periode tom er feil formattert") {
                val fil =
                    ftpFile(
                        this.testCase.name.testName,
                        getKravlinjer().apply {
                            add(get(0).copy(linjenummer = 6, periodeTOM = LineValidationRules.errorDate.toString()))
                        },
                    )
                Then("Skal validering returnere ValidationResult Error") {
                    errorValidation(fil)
                }
            }
        }
    })

private fun getKravlinjer(): MutableList<KravLinje> {
    val okLinje =
        KravLinje(
            1,
            "saksnummer",
            BigDecimal.ONE,
            LocalDate.now(),
            "gjelderID",
            "20231201",
            "20231212",
            "KS KS",
            "refgammelsak",
            "20230112",
            "bosted",
            "beh",
            "T",
            "arsak",
            BigDecimal.ONE,
            BigDecimal.ONE,
            LocalDate.now().minusDays(1),
            "1234",
        )
    return mutableListOf(okLinje, okLinje.copy(linjenummer = 2, saksnummerNav = "saksnummer2"), okLinje.copy(linjenummer = 3, saksnummerNav = "saksnummer3"), okLinje.copy(linjenummer = 4, saksnummerNav = "saksnummer4"), okLinje.copy(linjenummer = 5, saksnummerNav = "saksnummer5"))
}
