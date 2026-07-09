package no.nav.sokos.ske.krav.service.unit

import io.kotest.core.spec.style.FunSpec
import io.kotest.inspectors.forAll
import io.kotest.inspectors.forOne
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.clearMocks
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot

import no.nav.sokos.ske.krav.client.SlackClient
import no.nav.sokos.ske.krav.dto.slack.ErrorDetails
import no.nav.sokos.ske.krav.dto.slack.ExtraTags
import no.nav.sokos.ske.krav.dto.slack.ExtraTags.Companion.FANT_IKKE_GYLDIG_KRAVIDENTIFIKATOR
import no.nav.sokos.ske.krav.dto.slack.ExtraTags.Companion.ORGANISASJONSNUMMER_FINNES_IKKE
import no.nav.sokos.ske.krav.dto.slack.ExtraTags.Companion.ORGANISASJON_ER_SLETTET
import no.nav.sokos.ske.krav.dto.slack.ExtraTags.Companion.PERSON_EKSISTERER_IKKE
import no.nav.sokos.ske.krav.dto.slack.ExtraTags.Companion.PERSON_ER_DOED
import no.nav.sokos.ske.krav.dto.slack.ExtraTags.Companion.PERSON_ER_SLETTET
import no.nav.sokos.ske.krav.dto.slack.ExtraTags.Companion.REFERANSENUMMERGAMMELSAK_MANGLER
import no.nav.sokos.ske.krav.service.SlackService
import no.nav.sokos.ske.krav.util.shouldBe
import no.nav.sokos.ske.krav.util.shouldContain
import no.nav.sokos.ske.krav.validation.ErrorCategory
import no.nav.sokos.ske.krav.validation.ErrorCategory.FEIL_I_ASYNK_VALIDERING
import no.nav.sokos.ske.krav.validation.ErrorCategory.FEIL_I_LINJEVALIDERING
import no.nav.sokos.ske.krav.validation.ErrorCategory.FEIL_I_VALIDERING_AV_FIL
import no.nav.sokos.ske.krav.validation.ErrorKeys
import no.nav.sokos.ske.krav.validation.ErrorMessages

private const val PRODUCT_LEADER = "productLeaderId"
private const val DOMAIN_SPECIALIST = "domainSpecialistId"
private const val TECHNICAL_SPECIALIST = "technicalSpecialistId"

class SlackServiceTest :
    FunSpec({
        val slackClient =
            mockk<SlackClient> {
                coJustRun { sendMessage(any<ErrorCategory>(), any<String>(), any<ExtraTags>(), any<List<ErrorDetails>>()) }
            }
        val slackService = SlackService(slackClient)

        afterTest {
            slackService.clearErrorTracking()
            clearMocks(slackClient, answers = false)
        }

        test("addError legger en feil til SlackService") {
            val filename = "file3.txt"
            val errors =
                ErrorDetails(
                    ErrorKeys.PARSE_EXCEPTION,
                    "Unexpected token in line 42",
                )

            slackService.addError(
                filename,
                FEIL_I_VALIDERING_AV_FIL,
                errors,
            )

            val trackedErrors = slackService.trackedErrors()
            trackedErrors shouldHaveSize 1
            trackedErrors.first().should { fileError ->
                fileError.alertTitle shouldBe FEIL_I_VALIDERING_AV_FIL
                fileError.filename shouldBe filename
                fileError.extraTags.peopleSlackId.should { peopleSlackId ->
                    peopleSlackId shouldHaveSize 1
                    peopleSlackId.single() shouldContain PRODUCT_LEADER
                }
                fileError.errorDetails.should { errorDetails ->
                    errorDetails shouldHaveSize 1
                    errorDetails.first().should {
                        it.header shouldBe ErrorKeys.PARSE_EXCEPTION
                        it.description shouldBe "Unexpected token in line 42"
                        it.caseNumber.shouldBeNull()
                    }
                }
            }
        }

        test("addErrors samler feil sammen når de har samme tittel og filnavn") {
            val filename1 = "file1.txt"
            val filename2 = "file2.txt"

            val fileValidationErrors =
                List(3) {
                    ErrorDetails(
                        ErrorKeys.PARSE_EXCEPTION,
                        "Unexpected token in line 4$it",
                    )
                }

            val lineValidationError1 =
                ErrorDetails(
                    ErrorKeys.VEDTAKSDATO_ERROR,
                    ErrorMessages.VEDTAKSDATO_WRONG_FORMAT.description,
                    "saksnummer-123",
                )

            val lineValidationError2 =
                ErrorDetails(
                    ErrorKeys.KRAVTYPE_ERROR,
                    ErrorMessages.KRAVTYPE_DOES_NOT_EXIST.description,
                    "saksnummer-234",
                )

            val lineValidationError3 =
                ErrorDetails(
                    ErrorKeys.SAKSNUMMER_ERROR,
                    ErrorMessages.SAKSNUMMER_WRONG_FORMAT.description,
                    "saksnummer",
                )

            slackService.addErrors(filename1, FEIL_I_VALIDERING_AV_FIL, fileValidationErrors.take(2))
            slackService.addError(filename2, FEIL_I_VALIDERING_AV_FIL, fileValidationErrors.last())
            slackService.addError(filename1, FEIL_I_LINJEVALIDERING, lineValidationError1)
            slackService.addError(filename2, FEIL_I_LINJEVALIDERING, lineValidationError2)
            slackService.addError(filename2, FEIL_I_LINJEVALIDERING, lineValidationError3)

            slackService.trackedErrors().should { trackedErrors ->
                trackedErrors shouldHaveSize 4
                trackedErrors.forOne { fileError ->
                    fileError.alertTitle shouldBe FEIL_I_VALIDERING_AV_FIL
                    fileError.filename shouldBe filename1
                    fileError.errorDetails.should { errorDetails ->
                        errorDetails shouldHaveSize 2
                        errorDetails.forAll { (header, description, _) ->
                            header shouldBe ErrorKeys.PARSE_EXCEPTION
                            description shouldContain "Unexpected token in line 4[01]".toRegex()
                        }
                    }
                }
                trackedErrors.forOne { fileError ->
                    fileError.alertTitle shouldBe FEIL_I_VALIDERING_AV_FIL
                    fileError.filename shouldBe filename2
                    fileError.errorDetails.should { errorDetails ->
                        errorDetails shouldHaveSize 1
                        errorDetails.first().should {
                            it.header shouldBe ErrorKeys.PARSE_EXCEPTION
                            it.description shouldContain "Unexpected token in line 42"
                        }
                    }
                }
                trackedErrors.forOne { fileError ->
                    fileError.alertTitle shouldBe FEIL_I_LINJEVALIDERING
                    fileError.filename shouldBe filename1
                    fileError.errorDetails.should { errorDetails ->
                        errorDetails shouldHaveSize 1
                        errorDetails.first().should {
                            it.header shouldBe ErrorKeys.VEDTAKSDATO_ERROR
                            it.description shouldContain ErrorMessages.VEDTAKSDATO_WRONG_FORMAT
                            it.caseNumber shouldBe "saksnummer-123"
                        }
                    }
                }
                trackedErrors.forOne { fileError ->
                    fileError.alertTitle shouldBe FEIL_I_LINJEVALIDERING
                    fileError.filename shouldBe filename2
                    fileError.errorDetails.should { errorDetails ->
                        errorDetails shouldHaveSize 2
                        errorDetails.forOne { (header, description, caseNumber) ->
                            header shouldBe ErrorKeys.KRAVTYPE_ERROR
                            description shouldContain ErrorMessages.KRAVTYPE_DOES_NOT_EXIST
                            caseNumber shouldBe "saksnummer-234"
                        }

                        errorDetails.forOne { (header, description, caseNumber) ->
                            header shouldBe ErrorKeys.SAKSNUMMER_ERROR
                            description shouldContain ErrorMessages.SAKSNUMMER_WRONG_FORMAT
                            caseNumber shouldBe "saksnummer"
                        }
                    }
                }
            }
        }

        test("addError tagger alle riktige fagressurs og inkluderer rutinelenke når feilen er ORGANISASJON_ER_OPPHOERT") {
            val error =
                ErrorDetails(
                    ExtraTags.ORGANISASJON_ER_OPPHOERT,
                    "Organisasjon er opphoert.",
                )
            slackService.addError("file.txt", FEIL_I_ASYNK_VALIDERING, error)

            val trackedErrors = slackService.trackedErrors()
            with(trackedErrors.single().extraTags) {
                peopleSlackId.should {
                    it shouldHaveSize 2
                    it.forOne { slackId ->
                        slackId shouldContain DOMAIN_SPECIALIST
                    }
                    it.forOne { slackId ->
                        slackId shouldContain TECHNICAL_SPECIALIST
                    }
                }
                rutineLink.shouldNotBeEmpty()
            }
        }

        test("addError tagger produktlederen for alle de andre feilene") {
            val errors =
                listOf(
                    ErrorDetails(PERSON_EKSISTERER_IKKE, "Hva som helst"),
                    ErrorDetails(PERSON_ER_DOED, "Hva som helst"),
                    ErrorDetails(PERSON_ER_SLETTET, "Hva som helst"),
                    ErrorDetails(ORGANISASJONSNUMMER_FINNES_IKKE, "Hva som helst"),
                    ErrorDetails(ORGANISASJON_ER_SLETTET, "Hva som helst"),
                    ErrorDetails(FANT_IKKE_GYLDIG_KRAVIDENTIFIKATOR, "Hva som helst"),
                    ErrorDetails(REFERANSENUMMERGAMMELSAK_MANGLER, "Hva som helst"),
                    ErrorDetails("Unknown error", "Hva som helst"),
                )

            errors.forEachIndexed { index, errorDetails ->
                slackService.addError("file$index.txt", FEIL_I_ASYNK_VALIDERING, errorDetails)
            }

            val trackedErrors = slackService.trackedErrors()
            trackedErrors shouldHaveSize 8
            trackedErrors.forAll { fileError ->
                fileError.extraTags.peopleSlackId.should {
                    it shouldHaveSize 1
                    it.single() shouldContain PRODUCT_LEADER
                }
                fileError.extraTags.rutineLink.shouldBeEmpty()
            }
        }

        test("addErrors tagger alle riktige personer når det er flere forskjellige feil") {
            val error1 =
                ErrorDetails(
                    ExtraTags.ORGANISASJON_ER_OPPHOERT,
                    "Organisasjon er opphoert.",
                )

            val error2 =
                ErrorDetails(
                    "PERSON_EKSISTERER_IKKE",
                    "Person eksisterer ikke",
                )

            slackService.addErrors("file.txt", FEIL_I_ASYNK_VALIDERING, listOf(error1, error2))

            val trackedErrors = slackService.trackedErrors()
            with(trackedErrors.single().extraTags) {
                peopleSlackId.should {
                    it shouldHaveSize 3
                    it.forOne { slackId ->
                        slackId shouldContain DOMAIN_SPECIALIST
                    }
                    it.forOne { slackId ->
                        slackId shouldContain TECHNICAL_SPECIALIST
                    }
                    it.forOne { slackId ->
                        slackId shouldContain PRODUCT_LEADER
                    }
                }
                rutineLink.shouldNotBeEmpty()
            }
        }

        test("addErrors oppdaterer extraTags med nye personer når vi legger til en ny feil") {
            val error1 =
                ErrorDetails(
                    "PERSON_EKSISTERER_IKKE",
                    "Person eksisterer ikke",
                )

            val error2 =
                ErrorDetails(
                    ExtraTags.ORGANISASJON_ER_OPPHOERT,
                    "Organisasjon er opphoert.",
                )

            slackService.addError("file.txt", FEIL_I_ASYNK_VALIDERING, error1)
            slackService.addError("file.txt", FEIL_I_ASYNK_VALIDERING, error2)

            val trackedErrors = slackService.trackedErrors()
            with(trackedErrors.single().extraTags) {
                peopleSlackId.should {
                    it shouldHaveSize 3
                    it.forOne { slackId ->
                        slackId shouldContain DOMAIN_SPECIALIST
                    }
                    it.forOne { slackId ->
                        slackId shouldContain TECHNICAL_SPECIALIST
                    }
                    it.forOne { slackId ->
                        slackId shouldContain PRODUCT_LEADER
                    }
                }
                rutineLink.shouldNotBeEmpty()
            }
        }

        test("sendErrors erstatter ikke meldinger når det er <= 5 errors") {
            val errors =
                List(5) {
                    ErrorDetails(
                        ErrorKeys.PARSE_EXCEPTION,
                        "Unexpected token in line 4$it",
                    )
                }

            slackService.addErrors("file.txt", FEIL_I_VALIDERING_AV_FIL, errors)
            slackService.sendErrors()

            val errorsSlot = slot<List<ErrorDetails>>()
            coVerify(exactly = 1) { slackClient.sendMessage(any<ErrorCategory>(), any<String>(), any<ExtraTags>(), capture(errorsSlot)) }
            errorsSlot.captured.should {
                it shouldHaveSize 5
                it.forAll { (header, description, _) ->
                    header shouldBe ErrorKeys.PARSE_EXCEPTION
                    description shouldContain "Unexpected token in line 4[0-4]".toRegex()
                }
            }
        }

        test("sendErrors erstatter meldinger når det er >= 5 errors") {
            val errors =
                List(6) {
                    ErrorDetails(
                        ErrorKeys.PARSE_EXCEPTION,
                        "Unexpected token in line 4$it",
                        "saksnummer-11$it",
                    )
                }

            slackService.addErrors("file.txt", FEIL_I_VALIDERING_AV_FIL, errors)
            slackService.sendErrors()

            val errorsSlot = slot<List<ErrorDetails>>()
            coVerify(exactly = 1) { slackClient.sendMessage(any<ErrorCategory>(), any<String>(), any<ExtraTags>(), capture(errorsSlot)) }
            errorsSlot.captured.should {
                it shouldHaveSize 1
                it.single().should { errorDetails ->
                    errorDetails.header shouldBe ErrorKeys.PARSE_EXCEPTION
                    errorDetails.description shouldBe "6 av samme type feil: ${ErrorKeys.PARSE_EXCEPTION.value}. Sjekk avstemming"
                    errorDetails.caseNumber shouldBe "saksnummer-110, saksnummer-111, saksnummer-112, saksnummer-113, saksnummer-114, saksnummer-115"
                }
            }
        }

        test("sendErrors sender én slack melding per fil og feil type") {
            val filename1 = "file1.txt"
            val filename2 = "file2.txt"

            val fileValidationErrors =
                List(3) {
                    ErrorDetails(
                        ErrorKeys.PARSE_EXCEPTION,
                        "Unexpected token in line 4$it",
                    )
                }

            val asynkValideringErrors =
                List(7) {
                    ErrorDetails(
                        ExtraTags.ORGANISASJON_ER_OPPHOERT,
                        "Organisasjon er opphoert.",
                        caseNumber = "saksnummer-11$it",
                    )
                }

            slackService.addErrors(filename1, FEIL_I_VALIDERING_AV_FIL, fileValidationErrors.take(2))
            slackService.addError(filename2, FEIL_I_VALIDERING_AV_FIL, fileValidationErrors.last())
            slackService.addErrors(filename1, FEIL_I_ASYNK_VALIDERING, asynkValideringErrors.take(1))
            slackService.addErrors(filename2, FEIL_I_ASYNK_VALIDERING, asynkValideringErrors.takeLast(6))

            slackService.sendErrors()

            val alertCategories = mutableListOf<ErrorCategory>()
            val filenames = mutableListOf<String>()
            val extraTags = mutableListOf<ExtraTags>()
            val errors = mutableListOf<List<ErrorDetails>>()

            coVerify(exactly = 4) { slackClient.sendMessage(capture(alertCategories), capture(filenames), capture(extraTags), capture(errors)) }

            alertCategories.shouldContainExactly(FEIL_I_VALIDERING_AV_FIL, FEIL_I_VALIDERING_AV_FIL, FEIL_I_ASYNK_VALIDERING, FEIL_I_ASYNK_VALIDERING)
            filenames.shouldContainExactly(filename1, filename2, filename1, filename2)

            extraTags shouldHaveSize 4
            extraTags.take(2).forEach { tags ->
                tags.peopleSlackId.should { slackId ->
                    slackId shouldHaveSize 1
                    slackId.single() shouldContain PRODUCT_LEADER
                }
                tags.rutineLink.shouldBeEmpty()
            }

            extraTags.takeLast(2).forEach { tags ->
                tags.peopleSlackId.should { slackId ->
                    slackId shouldHaveSize 2
                    slackId.forOne {
                        it shouldContain DOMAIN_SPECIALIST
                    }
                    slackId.forOne {
                        it shouldContain TECHNICAL_SPECIALIST
                    }
                }
                tags.rutineLink.shouldNotBeEmpty()
            }

            errors shouldHaveSize 4
            errors.first().should {
                it shouldHaveSize 2
                it.forAll { (header, description, _) ->
                    header shouldBe ErrorKeys.PARSE_EXCEPTION
                    description shouldContain "Unexpected token in line 4[01]".toRegex()
                }
            }

            errors[1].should {
                it shouldHaveSize 1
                it.single().should { errorDetails ->
                    errorDetails.header shouldBe ErrorKeys.PARSE_EXCEPTION
                    errorDetails.description shouldBe "Unexpected token in line 42"
                    errorDetails.caseNumber.shouldBeNull()
                }
            }

            errors[2].should {
                it shouldHaveSize 1
                it.single().should { errorDetails ->
                    errorDetails.header shouldBe ExtraTags.ORGANISASJON_ER_OPPHOERT
                    errorDetails.description shouldBe "Organisasjon er opphoert."
                    errorDetails.caseNumber.shouldNotBeNull()
                }
            }

            errors.last().should {
                it shouldHaveSize 1
                it.single().should { errorDetails ->
                    errorDetails.header shouldBe ExtraTags.ORGANISASJON_ER_OPPHOERT
                    errorDetails.description shouldContain "av samme type feil"
                    errorDetails.caseNumber shouldContain "(saksnummer-11[1-6](, )?){6}".toRegex()
                }
            }
        }
    })
