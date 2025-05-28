package no.nav.sokos.ske.krav.validation

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.mockk.coVerify
import io.mockk.slot
import io.mockk.spyk
import no.nav.sokos.ske.krav.client.SlackClient
import no.nav.sokos.ske.krav.client.SlackService
import no.nav.sokos.ske.krav.config.SftpConfig
import no.nav.sokos.ske.krav.database.repository.ValideringsfeilRepository.getValideringsFeilForFil
import no.nav.sokos.ske.krav.domain.Status
import no.nav.sokos.ske.krav.service.DatabaseService
import no.nav.sokos.ske.krav.service.Directories
import no.nav.sokos.ske.krav.service.FtpService
import no.nav.sokos.ske.krav.util.MockHttpClient
import no.nav.sokos.ske.krav.util.SftpListener
import no.nav.sokos.ske.krav.util.TestContainer
import no.nav.sokos.ske.krav.validation.LineValidationRules.ErrorKeys
import no.nav.sokos.ske.krav.validation.LineValidationRules.ErrorMessages
import no.nav.sokos.ske.krav.validation.LineValidationRules.errorDate

internal class LineValidatorIntegrationTest :
    BehaviorSpec({
        extensions(SftpListener)
        val testContainer = TestContainer()

        fun setupServices(): Triple<SlackClient, SlackService, LineValidator> {
            val slackClientSpy = spyk(SlackClient(client = MockHttpClient().getSlackClient()))
            val slackServiceSpy = spyk(SlackService(slackClientSpy), recordPrivateCalls = true)
            val lineValidatorSpy = spyk(LineValidator(slackService = slackServiceSpy), recordPrivateCalls = true)
            return Triple(slackClientSpy, slackServiceSpy, lineValidatorSpy)
        }

        fun setupFtpService(
            dbService: DatabaseService,
            slackServiceSpy: SlackService,
        ): FtpService = FtpService(SftpConfig(SftpListener.sftpProperties), fileValidator = FileValidator(slackService = slackServiceSpy), databaseService = dbService)

        Given("Alle linjer er ok") {
            val dbService = DatabaseService(testContainer.dataSource)
            val (slackClientSpy, slackServiceSpy, lineValidatorSpy) = setupServices()
            val ftpService = setupFtpService(dbService, slackServiceSpy)
            val fileName = "AltOkFil.txt"
            SftpListener.putFiles(listOf(fileName), Directories.INBOUND)

            val ftpFil = ftpService.getValidatedFiles().first { it.name == fileName }

            When("Linjer valideres") {
                val validatedLines = lineValidatorSpy.validateNewLines(ftpFil, dbService)

                Then("Skal ingen feil lagres i database") {
                    testContainer.dataSource.connection
                        .getValideringsFeilForFil(fileName)
                        .size shouldBe 0
                }
                Then("Ingen linjer skal ha status VALIDERINGSFEIL_AV_LINJE_I_FIL") {
                    validatedLines.size shouldBe ftpFil.kravLinjer.size
                    validatedLines.filter { it.status == Status.VALIDERINGSFEIL_AV_LINJE_I_FIL.value }.size shouldBe 0
                }

                When("Feilmeldinger håndteres") {
                    Then("Feilmeldinger skal ikke dannes") {
                        coVerify(exactly = 0) {
                            slackServiceSpy.addError(any<String>(), any<String>(), any<List<Pair<String, String>>>())
                        }
                    }
                    Then("Alert skal ikke sendes") {
                        coVerify(exactly = 0) {
                            slackClientSpy.sendMessage(any<String>(), any<String>(), any<Map<String, List<String>>>())
                        }
                    }
                }
            }
        }

        Given("1 linje har 1 feil") {
            val dbService = DatabaseService(testContainer.dataSource)
            val (slackClientSpy, slackServiceSpy, lineValidatorSpy) = setupServices()
            val ftpService = setupFtpService(dbService, slackServiceSpy)
            val fileName = "1LinjeHarFeilKravtype.txt"
            SftpListener.putFiles(listOf(fileName), Directories.INBOUND)

            val ftpFil = ftpService.getValidatedFiles().first { it.name == fileName }

            When("Linjer valideres") {
                lineValidatorSpy.validateNewLines(ftpFil, dbService)

                Then("Skal én feil lagres i database") {
                    with(testContainer.dataSource.connection.getValideringsFeilForFil(fileName)) {
                        size shouldBe 1
                        with(first().feilmelding) {
                            shouldContain(ErrorMessages.KRAVTYPE_DOES_NOT_EXIST)
                            shouldNotContain(ErrorMessages.VEDTAKSDATO_WRONG_FORMAT)
                            shouldNotContain(ErrorMessages.VEDTAKSDATO_IS_IN_FUTURE)
                            shouldNotContain(ErrorMessages.UTBETALINGSDATO_WRONG_FORMAT)
                            shouldNotContain(ErrorMessages.UTBETALINGSDATO_IS_NOT_BEFORE_VEDTAKSDATO)
                            shouldNotContain(ErrorMessages.PERIODE_FOM_WRONG_FORMAT)
                            shouldNotContain(ErrorMessages.PERIODE_TOM_WRONG_FORMAT)
                            shouldNotContain(ErrorMessages.PERIODE_FOM_IS_AFTER_PERIODE_TOM)
                            shouldNotContain(ErrorMessages.PERIODE_TOM_IS_IN_INVALID_FUTURE)
                            shouldNotContain(ErrorMessages.UNKNOWN_DATE_ERROR)
                            shouldNotContain(ErrorMessages.SAKSNUMMER_WRONG_FORMAT)
                            shouldNotContain(ErrorMessages.REFERANSENUMMERGAMMELSAK_WRONG_FORMAT)
                        }
                    }
                }
                When("Feilmeldinger håndteres") {
                    val addErrorFilenameSlot = slot<String>()
                    val addErrorMessagesSlot = slot<List<Pair<String, String>>>()
                    coVerify(exactly = 1) {
                        slackServiceSpy.addError(capture(addErrorFilenameSlot), any<String>(), capture(addErrorMessagesSlot))
                    }

                    addErrorFilenameSlot.captured shouldBe fileName
                    val capturedSendAlertMessages: Map<String, List<String>> = addErrorMessagesSlot.captured.groupBy({ it.first }, { it.second })
                    Then("Skal én feilmelding dannes") {
                        capturedSendAlertMessages.size shouldBe 1

                        capturedSendAlertMessages[ErrorKeys.VEDTAKSDATO_ERROR] shouldBe null
                        capturedSendAlertMessages[ErrorKeys.UTBETALINGSDATO_ERROR] shouldBe null
                        capturedSendAlertMessages[ErrorKeys.PERIODE_ERROR] shouldBe null
                        capturedSendAlertMessages[ErrorKeys.SAKSNUMMER_ERROR] shouldBe null
                        capturedSendAlertMessages[ErrorKeys.REFERANSENUMMERGAMMELSAK_ERROR] shouldBe null
                        capturedSendAlertMessages[ErrorKeys.KRAVTYPE_ERROR] shouldNotBe null

                        with(capturedSendAlertMessages[ErrorKeys.KRAVTYPE_ERROR]!!) {
                            size shouldBe 1
                            first() shouldContain ErrorMessages.KRAVTYPE_DOES_NOT_EXIST
                        }
                    }
                    Then("Skal én feilmelding sendes") {
                        val sendAlertFilenameSlot = slot<String>()
                        val sendAlertMessagesSlot = slot<Map<String, List<String>>>()

                        coVerify(exactly = 1) {
                            slackClientSpy.sendMessage(any<String>(), capture(sendAlertFilenameSlot), capture(sendAlertMessagesSlot))
                        }
                        sendAlertFilenameSlot.captured shouldBe fileName

                        val capturedErrorMessages = sendAlertMessagesSlot.captured
                        capturedErrorMessages shouldBe capturedSendAlertMessages
                    }
                }
            }
        }

        Given("1 linje har 3 forskjellige feil") {
            testContainer.migrate()
            val dbService = DatabaseService(testContainer.dataSource)
            val (slackClientSpy, slackServiceSpy, lineValidatorSpy) = setupServices()
            val ftpService = setupFtpService(dbService, slackServiceSpy)
            val fileName = "1LinjeHarFeilSaksnummer_OgVedtaksdato_OgKravtype.txt"
            SftpListener.putFiles(listOf(fileName), Directories.INBOUND)

            val ftpFil = ftpService.getValidatedFiles().first { it.name == fileName }

            When("Linjer valideres") {
                val validatedLines = lineValidatorSpy.validateNewLines(ftpFil, dbService)

                Then("1 returnert linje skal ha status VALIDERINGSFEIL_AV_LINJE_I_FIL") {
                    validatedLines.size shouldBe ftpFil.kravLinjer.size
                    with(validatedLines.filter { it.status == Status.VALIDERINGSFEIL_AV_LINJE_I_FIL.value }) {
                        size shouldBe 1
                        filter { it.kravKode == "MJ AU" }.size shouldBe 1
                        filter { it.saksnummerNav == "saksnummer_øOB" }.size shouldBe 1
                    }
                }
                Then("Skal 3 feil lagres som én feilmelding i database") {
                    with(testContainer.dataSource.connection.getValideringsFeilForFil(fileName)) {
                        size shouldBe 1
                        with(first().feilmelding) {
                            shouldContain(ErrorMessages.SAKSNUMMER_WRONG_FORMAT)
                            shouldContain(ErrorMessages.VEDTAKSDATO_IS_IN_FUTURE)
                            shouldContain(ErrorMessages.KRAVTYPE_DOES_NOT_EXIST)

                            shouldNotContain(ErrorMessages.VEDTAKSDATO_WRONG_FORMAT)
                            shouldNotContain(ErrorMessages.UTBETALINGSDATO_WRONG_FORMAT)
                            shouldNotContain(ErrorMessages.UTBETALINGSDATO_IS_NOT_BEFORE_VEDTAKSDATO)
                            shouldNotContain(ErrorMessages.PERIODE_FOM_WRONG_FORMAT)
                            shouldNotContain(ErrorMessages.PERIODE_TOM_WRONG_FORMAT)
                            shouldNotContain(ErrorMessages.PERIODE_FOM_IS_AFTER_PERIODE_TOM)
                            shouldNotContain(ErrorMessages.PERIODE_TOM_IS_IN_INVALID_FUTURE)
                            shouldNotContain(ErrorMessages.UNKNOWN_DATE_ERROR)
                            shouldNotContain(ErrorMessages.REFERANSENUMMERGAMMELSAK_WRONG_FORMAT)
                        }
                    }
                }
                When("Feilmeldinger håndteres") {
                    val addErrorFilenameSlot = slot<String>()
                    val addErrorMessagesSlot = slot<List<Pair<String, String>>>()

                    coVerify(exactly = 1) {
                        slackServiceSpy.addError(capture(addErrorFilenameSlot), any<String>(), capture(addErrorMessagesSlot))
                    }

                    addErrorFilenameSlot.captured shouldBe fileName
                    val capturedAddErrorMessages: Map<String, List<String>> = addErrorMessagesSlot.captured.groupBy({ it.first }, { it.second })

                    Then("Skal 3 feilmeldinger dannes") {
                        capturedAddErrorMessages.size shouldBe 3

                        capturedAddErrorMessages[ErrorKeys.UTBETALINGSDATO_ERROR] shouldBe null
                        capturedAddErrorMessages[ErrorKeys.PERIODE_ERROR] shouldBe null
                        capturedAddErrorMessages[ErrorKeys.REFERANSENUMMERGAMMELSAK_ERROR] shouldBe null

                        capturedAddErrorMessages[ErrorKeys.VEDTAKSDATO_ERROR] shouldNotBe null
                        capturedAddErrorMessages[ErrorKeys.SAKSNUMMER_ERROR] shouldNotBe null
                        capturedAddErrorMessages[ErrorKeys.KRAVTYPE_ERROR] shouldNotBe null

                        with(capturedAddErrorMessages[ErrorKeys.VEDTAKSDATO_ERROR]!!) {
                            size shouldBe 1
                            first() shouldContain ErrorMessages.VEDTAKSDATO_IS_IN_FUTURE
                        }
                        with(capturedAddErrorMessages[ErrorKeys.SAKSNUMMER_ERROR]!!) {
                            size shouldBe 1
                            first() shouldContain ErrorMessages.SAKSNUMMER_WRONG_FORMAT
                        }

                        with(capturedAddErrorMessages[ErrorKeys.KRAVTYPE_ERROR]!!) {
                            size shouldBe 1
                            first() shouldContain ErrorMessages.KRAVTYPE_DOES_NOT_EXIST
                        }
                    }

                    Then("Skal 3 feilmeldinger sendes") {

                        val sendAlertFilenameSlot = slot<String>()
                        val sendAlertMessagesSlot = slot<Map<String, List<String>>>()

                        coVerify(exactly = 1) {
                            slackClientSpy.sendMessage(any<String>(), capture(sendAlertFilenameSlot), capture(sendAlertMessagesSlot))
                        }
                        sendAlertFilenameSlot.captured shouldBe fileName

                        val capturedErrorMessages = sendAlertMessagesSlot.captured
                        capturedErrorMessages shouldBe capturedAddErrorMessages
                    }
                }
            }
        }

        Given("6 linjer har samme type feil") {
            val fileName = "6LinjerHarSammeTypeFeil.txt"
            SftpListener.putFiles(listOf(fileName), Directories.INBOUND)

            val dbService = DatabaseService(testContainer.dataSource)
            val (slackClientSpy, slackServiceSpy, lineValidatorSpy) = setupServices()
            val ftpService = setupFtpService(dbService, slackServiceSpy)
            val ftpFil = ftpService.getValidatedFiles().first { it.name == fileName }
            ftpFil.kravLinjer.size shouldBe 10

            When("Linjer valideres") {
                val validatedLines = lineValidatorSpy.validateNewLines(ftpFil, dbService)
                Then("6 returnerte linjer skal ha status VALIDERINGSFEIL_AV_LINJE_I_FIL") {
                    validatedLines.size shouldBe ftpFil.kravLinjer.size
                    with(validatedLines.filter { it.status == Status.VALIDERINGSFEIL_AV_LINJE_I_FIL.value }) {
                        size shouldBe 6
                        filter { it.kravKode == "MJ AU" }.size shouldBe 6
                    }
                }

                Then("Skal 6 feil lagres i database") {
                    with(testContainer.dataSource.connection.getValideringsFeilForFil(fileName)) {
                        size shouldBe 6
                        all {
                            it.feilmelding.contains(ErrorMessages.KRAVTYPE_DOES_NOT_EXIST)
                            !it.feilmelding.contains(ErrorMessages.SAKSNUMMER_WRONG_FORMAT)
                            !it.feilmelding.contains(ErrorMessages.VEDTAKSDATO_IS_IN_FUTURE)
                            !it.feilmelding.contains(ErrorMessages.VEDTAKSDATO_WRONG_FORMAT)
                            !it.feilmelding.contains(ErrorMessages.UTBETALINGSDATO_WRONG_FORMAT)
                            !it.feilmelding.contains(ErrorMessages.UTBETALINGSDATO_IS_NOT_BEFORE_VEDTAKSDATO)
                            !it.feilmelding.contains(ErrorMessages.PERIODE_FOM_WRONG_FORMAT)
                            !it.feilmelding.contains(ErrorMessages.PERIODE_TOM_WRONG_FORMAT)
                            !it.feilmelding.contains(ErrorMessages.PERIODE_FOM_IS_AFTER_PERIODE_TOM)
                            !it.feilmelding.contains(ErrorMessages.PERIODE_TOM_IS_IN_INVALID_FUTURE)
                            !it.feilmelding.contains(ErrorMessages.UNKNOWN_DATE_ERROR)
                            !it.feilmelding.contains(ErrorMessages.REFERANSENUMMERGAMMELSAK_WRONG_FORMAT)
                        } shouldBe true
                    }
                }
                When("Feilmeldinger håndteres") {
                    val addErrorFilenameSlot = slot<String>()
                    val headerSlot = slot<String>()
                    val addErrorMessagesSlot = slot<List<Pair<String, String>>>()

                    coVerify(exactly = 1) {
                        slackServiceSpy.addError(capture(addErrorFilenameSlot), capture(headerSlot), capture(addErrorMessagesSlot))
                    }

                    addErrorFilenameSlot.captured shouldBe fileName
                    val capturedSendAlertMessages: Map<String, List<String>> = addErrorMessagesSlot.captured.groupBy({ it.first }, { it.second })

                    Then("Skal 6 feilmeldinger dannes") {

                        capturedSendAlertMessages.size shouldBe 1

                        capturedSendAlertMessages[ErrorKeys.VEDTAKSDATO_ERROR] shouldBe null
                        capturedSendAlertMessages[ErrorKeys.UTBETALINGSDATO_ERROR] shouldBe null
                        capturedSendAlertMessages[ErrorKeys.PERIODE_ERROR] shouldBe null
                        capturedSendAlertMessages[ErrorKeys.SAKSNUMMER_ERROR] shouldBe null
                        capturedSendAlertMessages[ErrorKeys.REFERANSENUMMERGAMMELSAK_ERROR] shouldBe null
                        capturedSendAlertMessages[ErrorKeys.KRAVTYPE_ERROR] shouldNotBe null
                        with(capturedSendAlertMessages[ErrorKeys.KRAVTYPE_ERROR]!!) {
                            size shouldBe 6
                            filter { it.contains(ErrorMessages.KRAVTYPE_DOES_NOT_EXIST) }.size shouldBe 6
                        }
                    }

                    Then("Skal 1 alert sendes") {
                        val sendAlertFilenameSlot = slot<String>()
                        val sendAlertMessagesSlot = slot<Map<String, List<String>>>()

                        coVerify(exactly = 1) {
                            slackClientSpy.sendMessage(any<String>(), capture(sendAlertFilenameSlot), capture(sendAlertMessagesSlot))
                        }
                        sendAlertFilenameSlot.captured shouldBe fileName

                        val capturedErrorMessages = sendAlertMessagesSlot.captured

                        capturedErrorMessages.size shouldBe 1
                        capturedErrorMessages.keys.first() shouldContain ErrorMessages.KRAVTYPE_DOES_NOT_EXIST
                    }
                }
            }
        }
        Given("6 linjer har samme type feil og 3 linjer har ulike feil") {
            val fileName = "6LinjerHarSammeTypeFeilOg3LinjerHarUlikeFeil.txt"
            SftpListener.putFiles(listOf(fileName), Directories.INBOUND)
            val dbService = DatabaseService(testContainer.dataSource)
            val (slackClientSpy, slackServiceSpy, lineValidatorSpy) = setupServices()
            val ftpService = setupFtpService(dbService, slackServiceSpy)
            val ftpFil = ftpService.getValidatedFiles().first { it.name == fileName }
            ftpFil.kravLinjer.size shouldBe 10
            When("Linjer valideres") {

                val validatedLines = lineValidatorSpy.validateNewLines(ftpFil, dbService)

                Then("6 returnerte linjer skal ha status VALIDERINGSFEIL_AV_LINJE_I_FIL") {
                    validatedLines.size shouldBe ftpFil.kravLinjer.size
                    with(validatedLines.filter { it.status == Status.VALIDERINGSFEIL_AV_LINJE_I_FIL.value }) {
                        size shouldBe 6
                        filter { it.kravKode == "MJ AU" }.size shouldBe 6
                        filter { it.saksnummerNav == "saksnummernav_ø" }.size shouldBe 1
                        filter { it.referansenummerGammelSak == "OB0refgammel_ø" }.size shouldBe 1
                        filter { it.vedtaksDato == errorDate }.size shouldBe 1
                    }
                }
                Then("Skal 6 feil lagres  i database ") {
                    with(testContainer.dataSource.connection.getValideringsFeilForFil(fileName)) {
                        size shouldBe 6
                        filter { it.feilmelding.contains(ErrorMessages.KRAVTYPE_DOES_NOT_EXIST) }.size shouldBe 6
                        filter { it.feilmelding.contains(ErrorMessages.VEDTAKSDATO_WRONG_FORMAT) }.size shouldBe 1
                        filter { it.feilmelding.contains(ErrorMessages.REFERANSENUMMERGAMMELSAK_WRONG_FORMAT) }.size shouldBe 1
                        filter { it.feilmelding.contains(ErrorMessages.SAKSNUMMER_WRONG_FORMAT) }.size shouldBe 1
                    }
                }

                When("Feilmeldinger håndteres") {
                    val addErrorFilenameSlot = slot<String>()
                    val addErrorMessageSlot = slot<MutableList<Pair<String, String>>>()

                    coVerify(exactly = 1) {
                        slackServiceSpy.addError(capture(addErrorFilenameSlot), any<String>(), capture(addErrorMessageSlot))
                    }

                    addErrorFilenameSlot.captured shouldBe fileName
                    val capturedSendAlertMessages: Map<String, List<String>> = addErrorMessageSlot.captured.groupBy({ it.first }, { it.second })
                    Then("Skal 9 feilmeldinger dannes") {
                        capturedSendAlertMessages.size shouldBe 4

                        capturedSendAlertMessages[ErrorKeys.UTBETALINGSDATO_ERROR] shouldBe null
                        capturedSendAlertMessages[ErrorKeys.PERIODE_ERROR] shouldBe null
                        capturedSendAlertMessages[ErrorKeys.VEDTAKSDATO_ERROR] shouldNotBe null
                        capturedSendAlertMessages[ErrorKeys.SAKSNUMMER_ERROR] shouldNotBe null
                        capturedSendAlertMessages[ErrorKeys.REFERANSENUMMERGAMMELSAK_ERROR] shouldNotBe null
                        capturedSendAlertMessages[ErrorKeys.KRAVTYPE_ERROR] shouldNotBe null

                        with(capturedSendAlertMessages[ErrorKeys.KRAVTYPE_ERROR]!!) {
                            size shouldBe 6
                            filter { it.contains(ErrorMessages.KRAVTYPE_DOES_NOT_EXIST) }.size shouldBe 6
                        }

                        with(capturedSendAlertMessages[ErrorKeys.VEDTAKSDATO_ERROR]!!) {
                            size shouldBe 1
                            filter { it.contains(ErrorMessages.VEDTAKSDATO_WRONG_FORMAT) }.size shouldBe 1
                        }

                        with(capturedSendAlertMessages[ErrorKeys.REFERANSENUMMERGAMMELSAK_ERROR]!!) {
                            size shouldBe 1
                            filter { it.contains(ErrorMessages.REFERANSENUMMERGAMMELSAK_WRONG_FORMAT) }.size shouldBe 1
                        }
                        with(capturedSendAlertMessages[ErrorKeys.SAKSNUMMER_ERROR]!!) {
                            size shouldBe 1
                            filter { it.contains(ErrorMessages.SAKSNUMMER_WRONG_FORMAT) }.size shouldBe 1
                        }
                    }

                    When("Alert sendes") {
                        val sendAlertFileNameSlot = slot<String>()
                        val sendAlertMessagesSlot = slot<Map<String, List<String>>>()

                        coVerify(exactly = 1) {
                            slackClientSpy.sendMessage(any<String>(), capture(sendAlertFileNameSlot), capture(sendAlertMessagesSlot))
                        }
                        sendAlertFileNameSlot.captured shouldBe fileName
                        val capturedErrorMessages = sendAlertMessagesSlot.captured
                        Then("Skal de 6 like feilmeldingene aggregeres til én") {
                            capturedErrorMessages.size shouldBe 4
                            capturedErrorMessages.keys.filter { it.contains(ErrorKeys.KRAVTYPE_ERROR) }.size shouldBe 1
                        }
                        Then("Skal de 3 ulike feilmeldingene ikke aggregeres") {
                            capturedErrorMessages.keys.filter { it.contains(ErrorKeys.VEDTAKSDATO_ERROR) }.size shouldBe 1
                            capturedErrorMessages.keys.filter { it.contains(ErrorKeys.REFERANSENUMMERGAMMELSAK_ERROR) }.size shouldBe 1
                            capturedErrorMessages.keys.filter { it.contains(ErrorKeys.SAKSNUMMER_ERROR) }.size shouldBe 1
                        }
                    }
                }
            }
        }
    })
