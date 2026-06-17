package no.nav.sokos.ske.krav.validation

import java.math.BigDecimal

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.inspectors.forAll
import io.kotest.inspectors.forExactly
import io.kotest.inspectors.forNone
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf

import no.nav.sokos.ske.krav.copybook.KravLinje
import no.nav.sokos.ske.krav.domain.Status
import no.nav.sokos.ske.krav.domain.Status.VALIDERINGSFEIL_AV_LINJE_I_FIL
import no.nav.sokos.ske.krav.util.FtpTestUtil.getFileContent

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

            Then("Skal validering returnere én ValidationResult.Failure og ${kravLines.size - 1} ValidationResult.Success") {
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
                errorKrav.first().messages.should { messages ->
                    messages shouldHaveSize 1
                    messages.forExactly(1) { (errorKey, message) ->
                        errorKey shouldBe ErrorKeys.KRAVTYPE_ERROR
                        message shouldContain ErrorMessages.KRAVTYPE_DOES_NOT_EXIST.description
                    }
                }
            }
        }

        Given("1 linje har 3 forskjellige feil") {
            val kravLines = getKravLinesFor("validering/linjevalidering/EnLinjeFlereFeil.txt")
            val validatedLines = lineValidator.validateNewLines(kravLines)
            val okKrav = validatedLines.filterIsInstance<ValidationResult.Success>()
            val errorKrav = validatedLines.filterIsInstance<ValidationResult.Error>()

            Then("Skal validering returnere 1 ValidationResult.Failuer og ${kravLines.size - 1} ValidationResult.Success") {
                validatedLines shouldHaveSize kravLines.size
                errorKrav shouldHaveSize 1
                okKrav shouldHaveSize kravLines.size - 1
            }

            And("Skal feil-linjene ha 3 forskjellige feilmelding") {
                errorKrav.first().messages.should { messages ->
                    messages shouldHaveSize 3
                    messages.forExactly(1) { (errorKey, message) ->
                        errorKey shouldBe ErrorKeys.SAKSNUMMER_ERROR
                        message shouldContain ErrorMessages.SAKSNUMMER_WRONG_FORMAT.description
                    }
                    messages.forExactly(1) { (errorKey, message) ->
                        errorKey shouldBe ErrorKeys.VEDTAKSDATO_ERROR
                        message shouldContain ErrorMessages.VEDTAKSDATO_IS_IN_FUTURE.description
                    }
                    messages.forExactly(1) { (errorKey, message) ->
                        errorKey shouldBe ErrorKeys.KRAVTYPE_ERROR
                        message shouldContain ErrorMessages.KRAVTYPE_DOES_NOT_EXIST.description
                    }

                    messages.forNone { (_, message) ->
                        message shouldContain ErrorMessages.VEDTAKSDATO_WRONG_FORMAT.description
                        message shouldContain ErrorMessages.UTBETALINGSDATO_WRONG_FORMAT.description
                        message shouldContain ErrorMessages.UTBETALINGSDATO_IS_NOT_BEFORE_VEDTAKSDATO.description
                        message shouldContain ErrorMessages.PERIODE_FOM_WRONG_FORMAT.description
                        message shouldContain ErrorMessages.PERIODE_TOM_WRONG_FORMAT.description
                        message shouldContain ErrorMessages.PERIODE_FOM_IS_AFTER_PERIODE_TOM.description
                        message shouldContain ErrorMessages.PERIODE_TOM_IS_IN_INVALID_FUTURE.description
                        message shouldContain ErrorMessages.UNKNOWN_DATE_ERROR.description
                        message shouldContain ErrorMessages.REFERANSENUMMERGAMMELSAK_WRONG_FORMAT.description
                        message shouldContain ErrorMessages.TILLEGGSFRISTDATO_TOO_OLD.description
                        message shouldContain ErrorMessages.TILLEGGSFRISTDATO_WRONG_FORMAT.description
                    }
                }
            }
        }

        Given("6 linjer har samme type feil") {
            val kravLines = getKravLinesFor("validering/linjevalidering/SeksLinjerSammeTypeFeil.txt")
            val validatedLines = lineValidator.validateNewLines(kravLines)
            val okKrav = validatedLines.filterIsInstance<ValidationResult.Success>()
            val errorKrav = validatedLines.filterIsInstance<ValidationResult.Error>()

            Then("Skal validering returnere 6 ValidationResult.Error og ${kravLines - 6} ValidationResult.Success") {
                validatedLines shouldHaveSize kravLines.size
                errorKrav shouldHaveSize 6
                okKrav shouldHaveSize kravLines.size - 6
            }

            And("Skal alle feil-linjene ha samme feil") {
                errorKrav.forAll {
                    it.messages shouldHaveSize 1
                    it.messages.forExactly(1) { (errorKey, message) ->
                        errorKey shouldBe ErrorKeys.KRAVTYPE_ERROR
                        message shouldContain ErrorMessages.KRAVTYPE_DOES_NOT_EXIST.description
                    }
                }
            }
        }

        Given("6 linjer har samme type feil og 3 av disse linjene har ulike feil") {
            val kravLines = getKravLinesFor("validering/linjevalidering/SeksLinjerSammeOgUlikeFeil.txt")
            val validatedLines = lineValidator.validateNewLines(kravLines)
            val okKrav = validatedLines.filterIsInstance<ValidationResult.Success>()
            val errorKrav = validatedLines.filterIsInstance<ValidationResult.Error>()

            Then("Skal validering returnere 6 ValidationResult.Error og ${kravLines - 6} ValidationResult.Success") {
                validatedLines shouldHaveSize kravLines.size
                okKrav shouldHaveSize kravLines.size - 6
                errorKrav shouldHaveSize 6
            }

            And("Skal feil-linjene ha 9 feil meldinger") {
                val errorMessages = errorKrav.flatMap { it.messages }
                errorMessages shouldHaveSize 9
                errorMessages.forExactly(6) { (errorKey, message) ->
                    errorKey shouldBe ErrorKeys.KRAVTYPE_ERROR
                    message shouldContain ErrorMessages.KRAVTYPE_DOES_NOT_EXIST.description
                }
                errorMessages.forExactly(1) { (errorKey, message) ->
                    errorKey shouldBe ErrorKeys.VEDTAKSDATO_ERROR
                    message shouldContain ErrorMessages.VEDTAKSDATO_WRONG_FORMAT.description
                }
                errorMessages.forExactly(1) { (errorKey, message) ->
                    errorKey shouldBe ErrorKeys.REFERANSENUMMERGAMMELSAK_ERROR
                    message shouldContain ErrorMessages.REFERANSENUMMERGAMMELSAK_WRONG_FORMAT.description
                }

                errorMessages.forExactly(1) { (errorKey, message) ->
                    errorKey shouldBe ErrorKeys.SAKSNUMMER_ERROR
                    message shouldContain ErrorMessages.SAKSNUMMER_WRONG_FORMAT.description
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
                    validatedKrav.messages shouldHaveSize 1
                    validatedKrav.messages.forExactly(1) { (errorKey, message) ->
                        errorKey shouldBe ErrorKeys.REFERANSENUMMERGAMMELSAK_MISSING
                        message shouldContain ErrorMessages.REFERANSENUMMERGAMMELSAK_MANGLER_FOR_STOPP.description
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
                    validatedKrav.messages shouldHaveSize 1
                    validatedKrav.messages.forExactly(1) { (errorKey, message) ->
                        errorKey shouldBe ErrorKeys.REFERANSENUMMERGAMMELSAK_ERROR
                        message shouldContain ErrorMessages.REFERANSENUMMERGAMMELSAK_WRONG_FORMAT.description
                    }
                }
            }

            When("Kravlinje er endring") {
                val validatedKrav = lineValidator.validateNewLines(listOf(endringKrav)).first()
                Then("Skal validering returnere ValidationResult.Error") {
                    validatedKrav.shouldBeInstanceOf<ValidationResult.Error>()
                    validatedKrav.messages shouldHaveSize 1
                    validatedKrav.messages.forExactly(1) { (errorKey, message) ->
                        errorKey shouldBe ErrorKeys.REFERANSENUMMERGAMMELSAK_ERROR
                        message shouldContain ErrorMessages.REFERANSENUMMERGAMMELSAK_WRONG_FORMAT.description
                    }
                }
            }

            When("Kravlinje er opprett") {
                val nyttKrav = kravLinje.find { it.isOpprettKrav() }?.invalidateReferensenummerGammelSak().shouldNotBeNull()
                val validatedKrav = lineValidator.validateNewLines(listOf(nyttKrav)).first()
                Then("Skal validering returnere ValidationResult.Error") {
                    validatedKrav.shouldBeInstanceOf<ValidationResult.Error>()
                    validatedKrav.messages shouldHaveSize 1
                    validatedKrav.messages.forExactly(1) { (errorKey, message) ->
                        errorKey shouldBe ErrorKeys.REFERANSENUMMERGAMMELSAK_ERROR
                        message shouldContain ErrorMessages.REFERANSENUMMERGAMMELSAK_WRONG_FORMAT.description
                    }
                }
            }
        }
    })
