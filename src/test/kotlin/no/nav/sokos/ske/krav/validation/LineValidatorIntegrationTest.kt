package no.nav.sokos.ske.krav.validation

import java.math.BigDecimal

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.inspectors.forAll
import io.kotest.inspectors.forExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

import no.nav.sokos.ske.krav.copybook.KravLinje
import no.nav.sokos.ske.krav.domain.Status
import no.nav.sokos.ske.krav.domain.Status.VALIDERINGSFEIL_AV_LINJE_I_FIL
import no.nav.sokos.ske.krav.util.FtpTestUtil.getFileContent
import no.nav.sokos.ske.krav.util.shouldBe
import no.nav.sokos.ske.krav.util.shouldContain
import no.nav.sokos.ske.krav.util.shouldNotContain

internal class LineValidatorIntegrationTest :
    BehaviorSpec({
        val fileValidator = FileValidator()
        val lineValidator = LineValidator()

        fun getKravLinesFor(filename: String): List<KravLinje> {
            val fileContent = getFileContent(filename)
            val validatedFile = fileValidator.validateFile(fileContent)

            return if (validatedFile is ValidationResult.Success) validatedFile.kravLinjer else emptyList()
        }

        Given("Alle linjer er ok") {
            val kravLines = getKravLinesFor("AllValideringOk.txt")
            val validatedLines = lineValidator.validateNewLines(kravLines)

            Then("Skal validerering returnere ${kravLines.size} ValidationResult.Success") {
                validatedLines shouldHaveSize kravLines.size
                validatedLines.forAll {
                    it.shouldBeInstanceOf<ValidationResult.Success>()
                }
            }

            And("Skal alle linjene ha status KRAV_IKKE_SENDT") {
                validatedLines.flatMap { (it as ValidationResult.Success).kravLinjer }.forAll {
                    it.status shouldBe Status.KRAV_IKKE_SENDT.value
                }
            }
        }

        Given("Én linje har én feil") {
            val kravLines = getKravLinesFor("validering/linjevalidering/EnLinjeFeilKravtype.txt")
            val validatedLines = lineValidator.validateNewLines(kravLines)
            val okKrav = validatedLines.filterIsInstance<ValidationResult.Success>()
            val errorKrav = validatedLines.filterIsInstance<ValidationResult.Error>()

            Then("Skal validering returnere én ValidationResult.Error og ${kravLines.size - 1} ValidationResult.Success") {
                validatedLines shouldHaveSize kravLines.size
                errorKrav shouldHaveSize 1
                okKrav shouldHaveSize kravLines.size - 1
            }

            And("Skal feil-linjen ha status VALIDERINGSFEIL_AV_LINJE_I_FIL") {
                errorKrav.first().originalLines?.forAll {
                    it.status shouldBe VALIDERINGSFEIL_AV_LINJE_I_FIL.value
                }
            }

            And("Skal feil-linjen ha én feilmelding") {
                errorKrav.first().errors.should { errors ->
                    errors shouldHaveSize 1
                    errors.first().should {
                        it.header shouldBe ErrorKeys.KRAVTYPE_ERROR
                        it.description shouldContain ErrorMessages.KRAVTYPE_DOES_NOT_EXIST
                        it.caseNumber.shouldNotBeNull()
                    }
                }
            }
        }

        Given("1 linje har 3 forskjellige feil") {
            val kravLines = getKravLinesFor("validering/linjevalidering/EnLinjeFlereFeil.txt")
            val validatedLines = lineValidator.validateNewLines(kravLines)
            val okKrav = validatedLines.filterIsInstance<ValidationResult.Success>()
            val errorKrav = validatedLines.filterIsInstance<ValidationResult.Error>()

            Then("Skal validering returnere 1 ValidationResult.Error og ${kravLines.size - 1} ValidationResult.Success") {
                validatedLines shouldHaveSize kravLines.size
                errorKrav shouldHaveSize 1
                okKrav shouldHaveSize kravLines.size - 1
            }

            And("Skal feil-linjene ha 3 forskjellige feilmelding") {
                errorKrav.first().errors.should { errors ->
                    errors shouldHaveSize 3
                    errors.forExactly(1) { (header, description, caseNumber) ->
                        header shouldBe ErrorKeys.SAKSNUMMER_ERROR
                        description shouldContain ErrorMessages.SAKSNUMMER_WRONG_FORMAT
                        caseNumber.shouldNotBeNull()
                    }
                    errors.forExactly(1) { (header, description, caseNumber) ->
                        header shouldBe ErrorKeys.VEDTAKSDATO_ERROR
                        description shouldContain ErrorMessages.VEDTAKSDATO_IS_IN_FUTURE
                        caseNumber.shouldNotBeNull()
                    }
                    errors.forExactly(1) { (header, description, caseNumber) ->
                        header shouldBe ErrorKeys.KRAVTYPE_ERROR
                        description shouldContain ErrorMessages.KRAVTYPE_DOES_NOT_EXIST
                        caseNumber.shouldNotBeNull()
                    }

                    errors.forAll { (_, description, _) ->
                        description shouldNotContain ErrorMessages.VEDTAKSDATO_WRONG_FORMAT
                        description shouldNotContain ErrorMessages.UTBETALINGSDATO_WRONG_FORMAT
                        description shouldNotContain ErrorMessages.UTBETALINGSDATO_IS_NOT_BEFORE_VEDTAKSDATO
                        description shouldNotContain ErrorMessages.PERIODE_FOM_WRONG_FORMAT
                        description shouldNotContain ErrorMessages.PERIODE_TOM_WRONG_FORMAT
                        description shouldNotContain ErrorMessages.PERIODE_FOM_IS_AFTER_PERIODE_TOM
                        description shouldNotContain ErrorMessages.PERIODE_TOM_IS_IN_INVALID_FUTURE
                        description shouldNotContain ErrorMessages.UNKNOWN_DATE_ERROR
                        description shouldNotContain ErrorMessages.REFERANSENUMMERGAMMELSAK_WRONG_FORMAT
                        description shouldNotContain ErrorMessages.TILLEGGSFRISTDATO_TOO_OLD
                        description shouldNotContain ErrorMessages.TILLEGGSFRISTDATO_WRONG_FORMAT
                    }
                }
            }
        }

        Given("6 linjer har samme type feil") {
            val kravLines = getKravLinesFor("validering/linjevalidering/SeksLinjerSammeTypeFeil.txt")
            val validatedLines = lineValidator.validateNewLines(kravLines)
            val okKrav = validatedLines.filterIsInstance<ValidationResult.Success>()
            val errorKrav = validatedLines.filterIsInstance<ValidationResult.Error>()

            Then("Skal validering returnere 6 ValidationResult.Error og ${kravLines.size - 6} ValidationResult.Success") {
                validatedLines shouldHaveSize kravLines.size
                errorKrav shouldHaveSize 6
                okKrav shouldHaveSize kravLines.size - 6
            }

            And("Skal alle feil-linjene ha samme feil") {
                errorKrav.forAll { errors ->
                    errors.errors shouldHaveSize 1
                    errors.errors.first().should {
                        it.header shouldBe ErrorKeys.KRAVTYPE_ERROR
                        it.description shouldContain ErrorMessages.KRAVTYPE_DOES_NOT_EXIST
                        it.caseNumber.shouldNotBeNull()
                    }
                }
            }
        }

        Given("6 linjer har samme type feil og 3 av disse linjene har ulike feil") {
            val kravLines = getKravLinesFor("validering/linjevalidering/SeksLinjerSammeOgUlikeFeil.txt")
            val validatedLines = lineValidator.validateNewLines(kravLines)
            val okKrav = validatedLines.filterIsInstance<ValidationResult.Success>()
            val errorKrav = validatedLines.filterIsInstance<ValidationResult.Error>()

            Then("Skal validering returnere 6 ValidationResult.Error og ${kravLines.size - 6} ValidationResult.Success") {
                validatedLines shouldHaveSize kravLines.size
                okKrav shouldHaveSize kravLines.size - 6
                errorKrav shouldHaveSize 6
            }

            And("Skal feil-linjene ha 9 feil meldinger") {
                val errorDetails = errorKrav.flatMap { it.errors }
                errorDetails shouldHaveSize 9
                errorDetails.forExactly(6) { (header, description, caseNumber) ->
                    header shouldBe ErrorKeys.KRAVTYPE_ERROR
                    description shouldContain ErrorMessages.KRAVTYPE_DOES_NOT_EXIST
                    caseNumber.shouldNotBeNull()
                }
                errorDetails.forExactly(1) { (errorKey, description, caseNumber) ->
                    errorKey shouldBe ErrorKeys.VEDTAKSDATO_ERROR
                    description shouldContain ErrorMessages.VEDTAKSDATO_WRONG_FORMAT
                    caseNumber.shouldNotBeNull()
                }
                errorDetails.forExactly(1) { (errorKey, description, caseNumber) ->
                    errorKey shouldBe ErrorKeys.REFERANSENUMMERGAMMELSAK_ERROR
                    description shouldContain ErrorMessages.REFERANSENUMMERGAMMELSAK_WRONG_FORMAT
                    caseNumber.shouldNotBeNull()
                }

                errorDetails.forExactly(1) { (errorKey, description, caseNumber) ->
                    errorKey shouldBe ErrorKeys.SAKSNUMMER_ERROR
                    description shouldContain ErrorMessages.SAKSNUMMER_WRONG_FORMAT
                    caseNumber.shouldNotBeNull()
                }
            }
        }

        Given("FagsystemId mangles") {
            fun List<KravLinje>.removeFagsystemIdForIndex(lineIndex: Int) =
                this.mapIndexed { index, line ->
                    if (index == lineIndex) line.copy(fagsystemId = "") else line
                }

            When("Avsender er OB04") {
                val kravLines = getKravLinesFor("innsender/OppdragFil.txt").removeFagsystemIdForIndex(0)

                Then("Skal validering returnere én ValidationResult.Error") {
                    val validatedLines = lineValidator.validateNewLines(kravLines)
                    validatedLines.first().shouldBeInstanceOf<ValidationResult.Error>()
                    validatedLines.subList(1, validatedLines.size).forAll {
                        it.shouldBeInstanceOf<ValidationResult.Success>()
                    }
                }
            }

            When("Avsender er Arena") {
                val kravLines = getKravLinesFor("innsender/ArenaFil.txt").removeFagsystemIdForIndex(0)

                Then("Skal validering returnere alle linjer som ValidationResult.Success") {
                    lineValidator.validateNewLines(kravLines).forAll {
                        it.shouldBeInstanceOf<ValidationResult.Success>()
                    }
                }
            }

            When("Avsender er Pesys") {
                val kravLines = getKravLinesFor("innsender/PesysFil.txt").removeFagsystemIdForIndex(0)

                Then("Skal validering returnere ValidationResult.Success") {
                    lineValidator.validateNewLines(kravLines).forAll {
                        it.shouldBeInstanceOf<ValidationResult.Success>()
                    }
                }
            }

            When("Avsender er Infotrygd") {
                val kravLines = getKravLinesFor("innsender/InfotrygdFil.txt").removeFagsystemIdForIndex(0)

                Then("Skal validering returnere ValidationResult.Success") {
                    lineValidator.validateNewLines(kravLines).forAll {
                        it.shouldBeInstanceOf<ValidationResult.Success>()
                    }
                }
            }
        }

        Given("ReferansenummerGammelSak er tom") {
            fun KravLinje.removeReferansenummerGammelSak() = copy(referansenummerGammelSak = "")
            val kravLinje = getKravLinesFor("innsender/PesysFil.txt")
            val stoppKrav = kravLinje.find { it.isStopp() }?.removeReferansenummerGammelSak().shouldNotBeNull()

            When("kravlinje er stopp") {
                Then("Skal validering returnere en ValidationResult.Error") {
                    val validatedKrav = lineValidator.validateNewLines(listOf(stoppKrav)).first()
                    validatedKrav.shouldBeInstanceOf<ValidationResult.Error>()
                    validatedKrav.errors shouldHaveSize 1
                    validatedKrav.errors.first().should {
                        it.header shouldBe ErrorKeys.REFERANSENUMMERGAMMELSAK_MISSING
                        it.description shouldContain ErrorMessages.REFERANSENUMMERGAMMELSAK_MANGLER_FOR_STOPP
                        it.caseNumber.shouldNotBeNull()
                    }
                }
            }

            When("kravlinje er opprett") {
                val nyttKrav = kravLinje.find { it.isOpprettKrav() }?.removeReferansenummerGammelSak().shouldNotBeNull()
                Then("Skal validering returnere ValidationResult.Success") {
                    val validatedKrav = lineValidator.validateNewLines(listOf(nyttKrav)).first()
                    validatedKrav.shouldBeInstanceOf<ValidationResult.Success>()
                }
            }
        }

        Given("ReferansenummerGammelSak er invalid") {
            fun KravLinje.invalidateReferensenummerGammelSak() = copy(referansenummerGammelSak = "!invalid?")
            val kravLinje = getKravLinesFor("innsender/PesysFil.txt")

            val stoppKrav = kravLinje.find { it.isStopp() }?.invalidateReferensenummerGammelSak().shouldNotBeNull()
            val endringKrav = stoppKrav.copy(belop = BigDecimal.ONE)

            When("KravLinje er stopp") {
                val validatedKrav = lineValidator.validateNewLines(listOf(stoppKrav)).first()
                Then("Skal validering returnere ValidationResult.Error") {
                    validatedKrav.shouldBeInstanceOf<ValidationResult.Error>()
                    validatedKrav.errors shouldHaveSize 1
                    validatedKrav.errors.first().should {
                        it.header shouldBe ErrorKeys.REFERANSENUMMERGAMMELSAK_ERROR
                        it.description shouldContain ErrorMessages.REFERANSENUMMERGAMMELSAK_WRONG_FORMAT
                        it.caseNumber.shouldNotBeNull()
                    }
                }
            }

            When("Kravlinje er endring") {
                val validatedKrav = lineValidator.validateNewLines(listOf(endringKrav)).first()
                Then("Skal validering returnere ValidationResult.Error") {
                    validatedKrav.shouldBeInstanceOf<ValidationResult.Error>()
                    validatedKrav.errors shouldHaveSize 1
                    validatedKrav.errors.first().should {
                        it.header shouldBe ErrorKeys.REFERANSENUMMERGAMMELSAK_ERROR
                        it.description shouldContain ErrorMessages.REFERANSENUMMERGAMMELSAK_WRONG_FORMAT
                        it.caseNumber.shouldNotBeNull()
                    }
                }
            }

            When("Kravlinje er opprett") {
                val nyttKrav = kravLinje.find { it.isOpprettKrav() }?.invalidateReferensenummerGammelSak().shouldNotBeNull()
                val validatedKrav = lineValidator.validateNewLines(listOf(nyttKrav)).first()
                Then("Skal validering returnere ValidationResult.Error") {
                    validatedKrav.shouldBeInstanceOf<ValidationResult.Error>()
                    validatedKrav.errors shouldHaveSize 1
                    validatedKrav.errors.first().should {
                        it.header shouldBe ErrorKeys.REFERANSENUMMERGAMMELSAK_ERROR
                        it.description shouldContain ErrorMessages.REFERANSENUMMERGAMMELSAK_WRONG_FORMAT
                        it.caseNumber.shouldNotBeNull()
                    }
                }
            }
        }
    })
