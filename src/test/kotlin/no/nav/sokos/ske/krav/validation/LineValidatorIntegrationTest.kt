package no.nav.sokos.ske.krav.validation

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.ktor.server.config.ApplicationConfig
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.spyk
import io.mockk.unmockkObject

import no.nav.sokos.ske.krav.client.SlackClient
import no.nav.sokos.ske.krav.client.SlackService
import no.nav.sokos.ske.krav.config.PropertiesConfig
import no.nav.sokos.ske.krav.config.SftpConfig
import no.nav.sokos.ske.krav.domain.Status
import no.nav.sokos.ske.krav.listener.DBListener
import no.nav.sokos.ske.krav.listener.SftpListener
import no.nav.sokos.ske.krav.repository.FilValideringsfeilRepository.getFilValideringsFeilForFil
import no.nav.sokos.ske.krav.service.DatabaseService
import no.nav.sokos.ske.krav.service.Directories
import no.nav.sokos.ske.krav.service.FtpService
import no.nav.sokos.ske.krav.util.MockHttpClient
import no.nav.sokos.ske.krav.validation.LineValidationRules.ErrorKeys
import no.nav.sokos.ske.krav.validation.LineValidationRules.ErrorMessages
import no.nav.sokos.ske.krav.validation.LineValidationRules.errorDate

internal class LineValidatorIntegrationTest :
    BehaviorSpec({
        extensions(SftpListener, DBListener)

        data class CapturedSendMessage(
            val header: String,
            val fileName: String,
            val messages: Map<String, List<String>>,
        )

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

        fun captureSlackMessages(slackClientSpy: SlackClient): MutableList<CapturedSendMessage> {
            val captured = mutableListOf<CapturedSendMessage>()
            coEvery { slackClientSpy.sendMessage(any(), any(), any()) } answers {
                captured.add(CapturedSendMessage(firstArg(), secondArg(), thirdArg()))
            }
            return captured
        }

        beforeSpec {
            mockkObject(PropertiesConfig)
            every { PropertiesConfig.config } returns ApplicationConfig("application-test.conf")
        }

        Given("Alle linjer er ok") {
            SftpListener.clearDirectory(Directories.INBOUND)
            val dbService = DatabaseService(DBListener.dataSource)
            val (slackClientSpy, slackServiceSpy, lineValidatorSpy) = setupServices()
            val ftpService = setupFtpService(dbService, slackServiceSpy)
            val fileName = "AltOkFil.txt"
            SftpListener.putFiles(listOf(fileName), Directories.INBOUND)

            val ftpFil = ftpService.getValidatedFiles().first { it.name == fileName }

            When("Linjer valideres") {
                val validatedLines = lineValidatorSpy.validateNewLines(ftpFil, dbService)

                Then("Ingen feil skal lagres, ingen linjer skal ha valideringsfeil-status og ingen alerter skal sendes") {
                    DBListener.dataSource.connection
                        .use { it.getFilValideringsFeilForFil(fileName) }
                        .size shouldBe 0

                    validatedLines.size shouldBe ftpFil.kravLinjer.size
                    validatedLines.filter { it.status == Status.VALIDERINGSFEIL_AV_LINJE_I_FIL.value }.size shouldBe 0

                    coVerify(exactly = 0) {
                        slackServiceSpy.addError(any<String>(), any<String>(), any<List<Pair<String, String>>>())
                    }
                    coVerify(exactly = 0) {
                        slackClientSpy.sendMessage(any<String>(), any<String>(), any<Map<String, List<String>>>())
                    }
                }
            }
        }

        Given("1 linje har 1 feil") {
            SftpListener.clearDirectory(Directories.INBOUND)
            val dbService = DatabaseService(DBListener.dataSource)
            val (slackClientSpy, slackServiceSpy, lineValidatorSpy) = setupServices()
            val ftpService = setupFtpService(dbService, slackServiceSpy)
            val capturedMessages = captureSlackMessages(slackClientSpy)
            val fileName = "1LinjeHarFeilKravtype.txt"
            SftpListener.putFiles(listOf(fileName), Directories.INBOUND)

            val ftpFil = ftpService.getValidatedFiles().first { it.name == fileName }

            When("Linjer valideres") {
                lineValidatorSpy.validateNewLines(ftpFil, dbService)

                Then("Skal én feil lagres i database, dannes og sendes som feilmelding") {
                    with(DBListener.dataSource.connection.use { it.getFilValideringsFeilForFil(fileName) }) {
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
                            shouldNotContain(ErrorMessages.TILLEGGSFRISTDATO_TOO_OLD)
                            shouldNotContain(ErrorMessages.TILLEGGSFRISTDATO_WRONG_FORMAT)
                        }
                    }

                    val addErrorFilenameSlot = slot<String>()
                    val addErrorMessagesSlot = slot<List<Pair<String, String>>>()
                    coVerify(exactly = 1) {
                        slackServiceSpy.addError(capture(addErrorFilenameSlot), any<String>(), capture(addErrorMessagesSlot))
                    }
                    addErrorFilenameSlot.captured shouldBe fileName
                    val capturedSendAlertMessages: Map<String, List<String>> = addErrorMessagesSlot.captured.groupBy({ it.first }, { it.second })

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

                    capturedMessages.size shouldBe 1
                    capturedMessages.first().fileName shouldBe fileName
                    capturedMessages.first().messages shouldBe capturedSendAlertMessages
                }
            }
        }

        Given("1 linje har 3 forskjellige feil") {
            SftpListener.clearDirectory(Directories.INBOUND)
            val dbService = DatabaseService(DBListener.dataSource)
            val (slackClientSpy, slackServiceSpy, lineValidatorSpy) = setupServices()
            val ftpService = setupFtpService(dbService, slackServiceSpy)
            val capturedMessages = captureSlackMessages(slackClientSpy)
            val fileName = "1LinjeHarFeilSaksnummer_OgVedtaksdato_OgKravtype.txt"
            SftpListener.putFiles(listOf(fileName), Directories.INBOUND)

            val ftpFil = ftpService.getValidatedFiles().first { it.name == fileName }

            When("Linjer valideres") {
                val validatedLines = lineValidatorSpy.validateNewLines(ftpFil, dbService)

                Then("Skal 3 feil lagres, returnert linje ha valideringsfeil-status, dannes og sendes") {
                    validatedLines.size shouldBe ftpFil.kravLinjer.size
                    with(validatedLines.filter { it.status == Status.VALIDERINGSFEIL_AV_LINJE_I_FIL.value }) {
                        size shouldBe 1
                        filter { it.kravKode == "MJ AU" }.size shouldBe 1
                        filter { it.saksnummerNav == "saksnummer_øOB" }.size shouldBe 1
                    }

                    with(DBListener.dataSource.connection.use { it.getFilValideringsFeilForFil(fileName) }) {
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
                            shouldNotContain(ErrorMessages.TILLEGGSFRISTDATO_TOO_OLD)
                            shouldNotContain(ErrorMessages.TILLEGGSFRISTDATO_WRONG_FORMAT)
                        }
                    }

                    val addErrorFilenameSlot = slot<String>()
                    val addErrorMessagesSlot = slot<List<Pair<String, String>>>()
                    coVerify(exactly = 1) {
                        slackServiceSpy.addError(capture(addErrorFilenameSlot), any<String>(), capture(addErrorMessagesSlot))
                    }
                    addErrorFilenameSlot.captured shouldBe fileName
                    val capturedAddErrorMessages: Map<String, List<String>> = addErrorMessagesSlot.captured.groupBy({ it.first }, { it.second })

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

                    capturedMessages.size shouldBe 1
                    capturedMessages.first().fileName shouldBe fileName
                    capturedMessages.first().messages shouldBe capturedAddErrorMessages
                }
            }
        }

        Given("6 linjer har samme type feil") {
            SftpListener.clearDirectory(Directories.INBOUND)
            val fileName = "6LinjerHarSammeTypeFeil.txt"
            SftpListener.putFiles(listOf(fileName), Directories.INBOUND)

            val dbService = DatabaseService(DBListener.dataSource)
            val (slackClientSpy, slackServiceSpy, lineValidatorSpy) = setupServices()
            val ftpService = setupFtpService(dbService, slackServiceSpy)
            val capturedMessages = captureSlackMessages(slackClientSpy)
            val ftpFil = ftpService.getValidatedFiles().first { it.name == fileName }
            ftpFil.kravLinjer.size shouldBe 10

            When("Linjer valideres") {
                val validatedLines = lineValidatorSpy.validateNewLines(ftpFil, dbService)

                Then("6 linjer har valideringsfeil-status, 6 feil lagres i database og 1 aggregert alert sendes") {
                    validatedLines.size shouldBe ftpFil.kravLinjer.size
                    with(validatedLines.filter { it.status == Status.VALIDERINGSFEIL_AV_LINJE_I_FIL.value }) {
                        size shouldBe 6
                        filter { it.kravKode == "MJ AU" }.size shouldBe 6
                    }

                    with(DBListener.dataSource.connection.use { it.getFilValideringsFeilForFil(fileName) }) {
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
                            !it.feilmelding.contains(ErrorMessages.TILLEGGSFRISTDATO_TOO_OLD)
                            !it.feilmelding.contains(ErrorMessages.TILLEGGSFRISTDATO_WRONG_FORMAT)
                        } shouldBe true
                    }

                    val addErrorFilenameSlot = slot<String>()
                    val headerSlot = slot<String>()
                    val addErrorMessagesSlot = slot<List<Pair<String, String>>>()
                    coVerify(exactly = 1) {
                        slackServiceSpy.addError(capture(addErrorFilenameSlot), capture(headerSlot), capture(addErrorMessagesSlot))
                    }
                    addErrorFilenameSlot.captured shouldBe fileName
                    val capturedSendAlertMessages: Map<String, List<String>> = addErrorMessagesSlot.captured.groupBy({ it.first }, { it.second })

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

                    capturedMessages.size shouldBe 1
                    capturedMessages.first().fileName shouldBe fileName
                    capturedMessages.first().messages.size shouldBe 1
                    capturedMessages
                        .first()
                        .messages.keys
                        .first() shouldContain ErrorMessages.KRAVTYPE_DOES_NOT_EXIST
                }
            }
        }

        Given("6 linjer har samme type feil og 3 linjer har ulike feil") {
            SftpListener.clearDirectory(Directories.INBOUND)
            val fileName = "6LinjerHarSammeTypeFeilOg3LinjerHarUlikeFeil.txt"
            SftpListener.putFiles(listOf(fileName), Directories.INBOUND)
            val dbService = DatabaseService(DBListener.dataSource)
            val (slackClientSpy, slackServiceSpy, lineValidatorSpy) = setupServices()
            val ftpService = setupFtpService(dbService, slackServiceSpy)
            val capturedMessages = captureSlackMessages(slackClientSpy)
            val ftpFil = ftpService.getValidatedFiles().first { it.name == fileName }
            ftpFil.kravLinjer.size shouldBe 10

            When("Linjer valideres") {
                val validatedLines = lineValidatorSpy.validateNewLines(ftpFil, dbService)

                Then("9 feilmeldinger dannes, 6 like aggregeres til én og 3 ulike sendes separat") {
                    validatedLines.size shouldBe ftpFil.kravLinjer.size
                    with(validatedLines.filter { it.status == Status.VALIDERINGSFEIL_AV_LINJE_I_FIL.value }) {
                        size shouldBe 6
                        filter { it.kravKode == "MJ AU" }.size shouldBe 6
                        filter { it.saksnummerNav == "saksnummernav_ø" }.size shouldBe 1
                        filter { it.referansenummerGammelSak == "OB0refgammel_ø" }.size shouldBe 1
                        filter { it.vedtaksDato.isEqual(errorDate) }.size shouldBe 1
                    }

                    with(DBListener.dataSource.connection.use { it.getFilValideringsFeilForFil(fileName) }) {
                        size shouldBe 6
                        filter { it.feilmelding.contains(ErrorMessages.KRAVTYPE_DOES_NOT_EXIST) }.size shouldBe 6
                        filter { it.feilmelding.contains(ErrorMessages.VEDTAKSDATO_WRONG_FORMAT) }.size shouldBe 1
                        filter { it.feilmelding.contains(ErrorMessages.REFERANSENUMMERGAMMELSAK_WRONG_FORMAT) }.size shouldBe 1
                        filter { it.feilmelding.contains(ErrorMessages.SAKSNUMMER_WRONG_FORMAT) }.size shouldBe 1
                    }

                    val addErrorFilenameSlot = slot<String>()
                    val addErrorMessageSlot = slot<MutableList<Pair<String, String>>>()
                    coVerify(exactly = 1) {
                        slackServiceSpy.addError(capture(addErrorFilenameSlot), any<String>(), capture(addErrorMessageSlot))
                    }
                    addErrorFilenameSlot.captured shouldBe fileName
                    val capturedSendAlertMessages: Map<String, List<String>> = addErrorMessageSlot.captured.groupBy({ it.first }, { it.second })

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

                    capturedMessages.size shouldBe 1
                    capturedMessages.first().fileName shouldBe fileName
                    val capturedErrorMessages = capturedMessages.first().messages
                    capturedErrorMessages.size shouldBe 4
                    capturedErrorMessages.keys.filter { it.contains(ErrorKeys.KRAVTYPE_ERROR) }.size shouldBe 1
                    capturedErrorMessages.keys.filter { it.contains(ErrorKeys.VEDTAKSDATO_ERROR) }.size shouldBe 1
                    capturedErrorMessages.keys.filter { it.contains(ErrorKeys.REFERANSENUMMERGAMMELSAK_ERROR) }.size shouldBe 1
                    capturedErrorMessages.keys.filter { it.contains(ErrorKeys.SAKSNUMMER_ERROR) }.size shouldBe 1
                }
            }
        }

        afterSpec {
            unmockkObject(PropertiesConfig)
        }
    })
