package no.nav.sokos.ske.krav.validation

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.maps.shouldHaveSize
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
import no.nav.sokos.ske.krav.domain.Status
import no.nav.sokos.ske.krav.domain.TaggablePeople
import no.nav.sokos.ske.krav.listener.DBListener
import no.nav.sokos.ske.krav.listener.DBListener.dataSource
import no.nav.sokos.ske.krav.listener.DBListener.filvalideringsFeilRepository
import no.nav.sokos.ske.krav.listener.SftpListener
import no.nav.sokos.ske.krav.service.Directories
import no.nav.sokos.ske.krav.service.FtpService
import no.nav.sokos.ske.krav.util.getFilValideringsFeilForFil
import no.nav.sokos.ske.krav.util.http.MockHttpClient
import no.nav.sokos.ske.krav.util.transaction
import no.nav.sokos.ske.krav.validation.LineValidationRules.ErrorKeys
import no.nav.sokos.ske.krav.validation.LineValidationRules.ErrorMessages
import no.nav.sokos.ske.krav.validation.LineValidationRules.errorDate

internal class LineValidatorIntegrationTest :
    BehaviorSpec({
        extensions(SftpListener, DBListener)

        fun setupServices(): Triple<SlackClient, SlackService, LineValidator> {
            val slackClientSpy = spyk(SlackClient(client = MockHttpClient.slackClient))
            val slackServiceSpy = spyk(SlackService(slackClientSpy), recordPrivateCalls = true)
            val lineValidatorSpy = spyk(LineValidator(dataSource, filvalideringsFeilRepository, slackService = slackServiceSpy), recordPrivateCalls = true)
            return Triple(slackClientSpy, slackServiceSpy, lineValidatorSpy)
        }

        fun setupFtpService(slackServiceSpy: SlackService): FtpService =
            FtpService(
                dataSource = dataSource,
                sftpConfig = SftpConfig(SftpListener.sftpProperties),
                fileValidator = FileValidator(slackService = slackServiceSpy),
                filValideringsfeilRepository = filvalideringsFeilRepository,
            )

        Given("Alle linjer er ok") {
            val (slackClientSpy, slackServiceSpy, lineValidatorSpy) = setupServices()
            val ftpService = setupFtpService(slackServiceSpy)
            val fileName = "AllValideringOk.txt"
            SftpListener.putFiles(listOf(fileName), Directories.INBOUND)

            val ftpFil = ftpService.getValidatedFiles().first { it.name == fileName }

            When("Linjer valideres") {
                val validatedLines = lineValidatorSpy.validateNewLines(ftpFil)

                Then("Skal ingen feil lagres i database") {
                    val insertedFiles =
                        dataSource.transaction { session ->
                            filvalideringsFeilRepository.getFilValideringsFeilForFil(session, fileName)
                        }
                    insertedFiles.shouldBeEmpty()
                }

                Then("Ingen linjer skal ha status VALIDERINGSFEIL_AV_LINJE_I_FIL") {
                    validatedLines.shouldHaveSize(ftpFil.kravLinjer.size)
                    validatedLines.filter { it.status == Status.VALIDERINGSFEIL_AV_LINJE_I_FIL.value }.shouldBeEmpty()
                }
            }
            When("Feilmeldinger håndteres") {
                Then("Feilmeldinger skal ikke dannes") {
                    coVerify(exactly = 0) {
                        slackServiceSpy.addError(any<String>(), any<String>(), any<List<Pair<String, String>>>())
                    }
                }
                Then("Alert skal ikke sendes") {
                    coVerify(exactly = 0) {
                        slackClientSpy.sendMessage(any<String>(), any<String>(), any<Map<String, List<String>>>(), any<List<TaggablePeople>>(), any())
                    }
                }
            }
        }

        Given("1 linje har 1 feil") {
            val (slackClientSpy, slackServiceSpy, lineValidatorSpy) = setupServices()
            val ftpService = setupFtpService(slackServiceSpy)
            val fileName = "validering/linjevalidering/EnLinjeFeilKravtype.txt"
            val fileNameOnSftp = fileName.substringAfterLast("/")
            SftpListener.putFiles(listOf(fileName), Directories.INBOUND)

            val ftpFil = ftpService.getValidatedFiles().first { it.name == fileNameOnSftp }

            When("Linjer valideres") {
                lineValidatorSpy.validateNewLines(ftpFil)

                Then("Skal én feil lagres i database") {
                    val insertedFiles =
                        dataSource.transaction { session ->
                            filvalideringsFeilRepository.getFilValideringsFeilForFil(session, fileNameOnSftp)
                        }
                    insertedFiles.shouldHaveSize(1)
                    with(insertedFiles.first().feilmelding) {
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
            }
            When("Feilmeldinger håndteres") {
                val addErrorFilenameSlot = slot<String>()
                val addErrorMessagesSlot = slot<List<Pair<String, String>>>()
                coVerify(exactly = 1) {
                    slackServiceSpy.addError(capture(addErrorFilenameSlot), any<String>(), capture(addErrorMessagesSlot))
                }

                addErrorFilenameSlot.captured shouldBe fileNameOnSftp
                val capturedSendAlertMessages: Map<String, List<String>> = addErrorMessagesSlot.captured.groupBy({ it.first }, { it.second })
                Then("Skal én feilmelding dannes") {
                    capturedSendAlertMessages.shouldHaveSize(1)

                    capturedSendAlertMessages[ErrorKeys.VEDTAKSDATO_ERROR] shouldBe null
                    capturedSendAlertMessages[ErrorKeys.UTBETALINGSDATO_ERROR] shouldBe null
                    capturedSendAlertMessages[ErrorKeys.PERIODE_ERROR] shouldBe null
                    capturedSendAlertMessages[ErrorKeys.SAKSNUMMER_ERROR] shouldBe null
                    capturedSendAlertMessages[ErrorKeys.REFERANSENUMMERGAMMELSAK_ERROR] shouldBe null
                    capturedSendAlertMessages[ErrorKeys.KRAVTYPE_ERROR] shouldNotBe null

                    with(capturedSendAlertMessages[ErrorKeys.KRAVTYPE_ERROR]!!) {
                        shouldHaveSize(1)
                        first() shouldContain ErrorMessages.KRAVTYPE_DOES_NOT_EXIST
                    }
                }
                Then("Skal én feilmelding sendes") {
                    val sendAlertFilenameSlot = slot<String>()
                    val sendAlertMessagesSlot = slot<Map<String, List<String>>>()

                    coVerify(exactly = 1) {
                        slackClientSpy.sendMessage(any<String>(), capture(sendAlertFilenameSlot), capture(sendAlertMessagesSlot), any<List<TaggablePeople>>(), any())
                    }
                    sendAlertFilenameSlot.captured shouldBe fileNameOnSftp

                    val capturedErrorMessages = sendAlertMessagesSlot.captured
                    capturedErrorMessages shouldBe capturedSendAlertMessages
                }
            }
        }

        Given("1 linje har 3 forskjellige feil") {
            val (slackClientSpy, slackServiceSpy, lineValidatorSpy) = setupServices()
            val ftpService = setupFtpService(slackServiceSpy)
            val fileName = "validering/linjevalidering/EnLinjeFlereFeil.txt"
            val fileNameOnSftp = fileName.substringAfterLast("/")
            SftpListener.putFiles(listOf(fileName), Directories.INBOUND)

            val ftpFil = ftpService.getValidatedFiles().first { it.name == fileNameOnSftp }

            When("Linjer valideres") {
                val validatedLines = lineValidatorSpy.validateNewLines(ftpFil)

                Then("1 returnert linje skal ha status VALIDERINGSFEIL_AV_LINJE_I_FIL") {
                    validatedLines.shouldHaveSize(ftpFil.kravLinjer.size)
                    with(validatedLines.filter { it.status == Status.VALIDERINGSFEIL_AV_LINJE_I_FIL.value }) {
                        shouldHaveSize(1)
                        filter { it.kravKode == "MJ AU" }.shouldHaveSize(1)
                        filter { it.saksnummerNav == "saksnummer_øOB" }.shouldHaveSize(1)
                    }
                }
                Then("Skal 3 feil lagres som én feilmelding i database") {
                    val insertedFiles =
                        dataSource.transaction { session ->
                            filvalideringsFeilRepository.getFilValideringsFeilForFil(session, fileNameOnSftp)
                        }

                    insertedFiles.shouldHaveSize(1)
                    with(insertedFiles.first().feilmelding) {
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
            }
            When("Feilmeldinger håndteres") {
                val addErrorFilenameSlot = slot<String>()
                val addErrorMessagesSlot = slot<List<Pair<String, String>>>()

                coVerify(exactly = 1) {
                    slackServiceSpy.addError(capture(addErrorFilenameSlot), any<String>(), capture(addErrorMessagesSlot))
                }

                addErrorFilenameSlot.captured shouldBe fileNameOnSftp
                val capturedAddErrorMessages: Map<String, List<String>> = addErrorMessagesSlot.captured.groupBy({ it.first }, { it.second })

                Then("Skal 3 feilmeldinger dannes") {
                    capturedAddErrorMessages.shouldHaveSize(3)

                    capturedAddErrorMessages[ErrorKeys.UTBETALINGSDATO_ERROR] shouldBe null
                    capturedAddErrorMessages[ErrorKeys.PERIODE_ERROR] shouldBe null
                    capturedAddErrorMessages[ErrorKeys.REFERANSENUMMERGAMMELSAK_ERROR] shouldBe null

                    capturedAddErrorMessages[ErrorKeys.VEDTAKSDATO_ERROR] shouldNotBe null
                    capturedAddErrorMessages[ErrorKeys.SAKSNUMMER_ERROR] shouldNotBe null
                    capturedAddErrorMessages[ErrorKeys.KRAVTYPE_ERROR] shouldNotBe null

                    with(capturedAddErrorMessages[ErrorKeys.VEDTAKSDATO_ERROR]!!) {
                        shouldHaveSize(1)
                        first() shouldContain ErrorMessages.VEDTAKSDATO_IS_IN_FUTURE
                    }
                    with(capturedAddErrorMessages[ErrorKeys.SAKSNUMMER_ERROR]!!) {
                        shouldHaveSize(1)
                        first() shouldContain ErrorMessages.SAKSNUMMER_WRONG_FORMAT
                    }

                    with(capturedAddErrorMessages[ErrorKeys.KRAVTYPE_ERROR]!!) {
                        shouldHaveSize(1)
                        first() shouldContain ErrorMessages.KRAVTYPE_DOES_NOT_EXIST
                    }
                }

                Then("Skal 3 feilmeldinger sendes") {

                    val sendAlertFilenameSlot = slot<String>()
                    val sendAlertMessagesSlot = slot<Map<String, List<String>>>()

                    coVerify(exactly = 1) {
                        slackClientSpy.sendMessage(any<String>(), capture(sendAlertFilenameSlot), capture(sendAlertMessagesSlot), any<List<TaggablePeople>>(), any())
                    }
                    sendAlertFilenameSlot.captured shouldBe fileNameOnSftp

                    val capturedErrorMessages = sendAlertMessagesSlot.captured
                    capturedErrorMessages shouldBe capturedAddErrorMessages
                }
            }
        }

        Given("6 linjer har samme type feil") {
            val fileName = "validering/linjevalidering/SeksLinjerSammeTypeFeil.txt"
            val fileNameOnSftp = fileName.substringAfterLast("/")
            SftpListener.putFiles(listOf(fileName), Directories.INBOUND)

            val (slackClientSpy, slackServiceSpy, lineValidatorSpy) = setupServices()
            val ftpService = setupFtpService(slackServiceSpy)
            val ftpFil = ftpService.getValidatedFiles().first { it.name == fileNameOnSftp }
            ftpFil.kravLinjer.shouldHaveSize(10)

            When("Linjer valideres") {
                val validatedLines = lineValidatorSpy.validateNewLines(ftpFil)
                Then("6 returnerte linjer skal ha status VALIDERINGSFEIL_AV_LINJE_I_FIL") {
                    validatedLines.shouldHaveSize(ftpFil.kravLinjer.size)
                    with(validatedLines.filter { it.status == Status.VALIDERINGSFEIL_AV_LINJE_I_FIL.value }) {
                        shouldHaveSize(6)
                        filter { it.kravKode == "MJ AU" }.shouldHaveSize(6)
                    }
                }

                Then("Skal 6 feil lagres i database") {
                    val insertedFiles =
                        dataSource.transaction { session ->
                            filvalideringsFeilRepository.getFilValideringsFeilForFil(session, fileNameOnSftp)
                        }

                    insertedFiles.shouldHaveSize(6)
                    insertedFiles.forAll {
                        with(it.feilmelding) {
                            shouldContain(ErrorMessages.KRAVTYPE_DOES_NOT_EXIST)

                            shouldNotContain(ErrorMessages.SAKSNUMMER_WRONG_FORMAT)
                            shouldNotContain(ErrorMessages.VEDTAKSDATO_IS_IN_FUTURE)
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
                }
            }
            When("Feilmeldinger håndteres") {
                val addErrorFilenameSlot = slot<String>()
                val headerSlot = slot<String>()
                val addErrorMessagesSlot = slot<List<Pair<String, String>>>()

                coVerify(exactly = 1) {
                    slackServiceSpy.addError(capture(addErrorFilenameSlot), capture(headerSlot), capture(addErrorMessagesSlot))
                }

                addErrorFilenameSlot.captured shouldBe fileNameOnSftp
                val capturedSendAlertMessages: Map<String, List<String>> = addErrorMessagesSlot.captured.groupBy({ it.first }, { it.second })

                Then("Skal 6 feilmeldinger dannes") {

                    capturedSendAlertMessages.shouldHaveSize(1)

                    capturedSendAlertMessages[ErrorKeys.VEDTAKSDATO_ERROR] shouldBe null
                    capturedSendAlertMessages[ErrorKeys.UTBETALINGSDATO_ERROR] shouldBe null
                    capturedSendAlertMessages[ErrorKeys.PERIODE_ERROR] shouldBe null
                    capturedSendAlertMessages[ErrorKeys.SAKSNUMMER_ERROR] shouldBe null
                    capturedSendAlertMessages[ErrorKeys.REFERANSENUMMERGAMMELSAK_ERROR] shouldBe null
                    capturedSendAlertMessages[ErrorKeys.KRAVTYPE_ERROR] shouldNotBe null
                    with(capturedSendAlertMessages[ErrorKeys.KRAVTYPE_ERROR]!!) {
                        shouldHaveSize(6)
                        filter { it.contains(ErrorMessages.KRAVTYPE_DOES_NOT_EXIST) }.shouldHaveSize(6)
                    }
                }

                Then("Skal 1 alert sendes") {
                    val sendAlertFilenameSlot = slot<String>()
                    val sendAlertMessagesSlot = slot<Map<String, List<String>>>()

                    coVerify(exactly = 1) {
                        slackClientSpy.sendMessage(any<String>(), capture(sendAlertFilenameSlot), capture(sendAlertMessagesSlot), any<List<TaggablePeople>>(), any())
                    }
                    sendAlertFilenameSlot.captured shouldBe fileNameOnSftp

                    val capturedErrorMessages = sendAlertMessagesSlot.captured

                    capturedErrorMessages.shouldHaveSize(1)
                    capturedErrorMessages.keys.first() shouldContain ErrorMessages.KRAVTYPE_DOES_NOT_EXIST
                }
            }
        }
        Given("6 linjer har samme type feil og 3 linjer har ulike feil") {
            val fileName = "validering/linjevalidering/SeksLinjerSammeOgUlikeFeil.txt"
            val fileNameOnSftp = fileName.substringAfterLast("/")
            SftpListener.putFiles(listOf(fileName), Directories.INBOUND)
            val (slackClientSpy, slackServiceSpy, lineValidatorSpy) = setupServices()
            val ftpService = setupFtpService(slackServiceSpy)
            val ftpFil = ftpService.getValidatedFiles().first { it.name == fileNameOnSftp }
            ftpFil.kravLinjer.shouldHaveSize(10)
            When("Linjer valideres") {

                val validatedLines = lineValidatorSpy.validateNewLines(ftpFil)

                Then("6 returnerte linjer skal ha status VALIDERINGSFEIL_AV_LINJE_I_FIL") {
                    validatedLines.shouldHaveSize(ftpFil.kravLinjer.size)
                    with(validatedLines.filter { it.status == Status.VALIDERINGSFEIL_AV_LINJE_I_FIL.value }) {
                        shouldHaveSize(6)
                        filter { it.kravKode == "MJ AU" }.shouldHaveSize(6)
                        filter { it.saksnummerNav == "saksnummernav_ø" }.shouldHaveSize(1)
                        filter { it.referansenummerGammelSak == "OB0refgammel_ø" }.shouldHaveSize(1)
                        filter { it.vedtaksDato.isEqual(errorDate) }.shouldHaveSize(1)
                    }
                }
                Then("Skal 6 feil lagres  i database ") {
                    val insertedFiles =
                        dataSource.transaction { session ->
                            filvalideringsFeilRepository.getFilValideringsFeilForFil(session, fileNameOnSftp)
                        }

                    insertedFiles.shouldHaveSize(6)
                    with(insertedFiles) {
                        filter { it.feilmelding.contains(ErrorMessages.KRAVTYPE_DOES_NOT_EXIST) }.shouldHaveSize(6)
                        filter { it.feilmelding.contains(ErrorMessages.VEDTAKSDATO_WRONG_FORMAT) }.shouldHaveSize(1)
                        filter { it.feilmelding.contains(ErrorMessages.REFERANSENUMMERGAMMELSAK_WRONG_FORMAT) }.shouldHaveSize(1)
                        filter { it.feilmelding.contains(ErrorMessages.SAKSNUMMER_WRONG_FORMAT) }.shouldHaveSize(1)
                    }
                }
            }
            When("Feilmeldinger håndteres") {
                val addErrorFilenameSlot = slot<String>()
                val addErrorMessageSlot = slot<MutableList<Pair<String, String>>>()

                coVerify(exactly = 1) {
                    slackServiceSpy.addError(capture(addErrorFilenameSlot), any<String>(), capture(addErrorMessageSlot))
                }

                addErrorFilenameSlot.captured shouldBe fileNameOnSftp
                val capturedSendAlertMessages: Map<String, List<String>> = addErrorMessageSlot.captured.groupBy({ it.first }, { it.second })
                Then("Skal 9 feilmeldinger dannes") {
                    capturedSendAlertMessages.shouldHaveSize(4)

                    capturedSendAlertMessages[ErrorKeys.UTBETALINGSDATO_ERROR] shouldBe null
                    capturedSendAlertMessages[ErrorKeys.PERIODE_ERROR] shouldBe null
                    capturedSendAlertMessages[ErrorKeys.VEDTAKSDATO_ERROR] shouldNotBe null
                    capturedSendAlertMessages[ErrorKeys.SAKSNUMMER_ERROR] shouldNotBe null
                    capturedSendAlertMessages[ErrorKeys.REFERANSENUMMERGAMMELSAK_ERROR] shouldNotBe null
                    capturedSendAlertMessages[ErrorKeys.KRAVTYPE_ERROR] shouldNotBe null

                    with(capturedSendAlertMessages[ErrorKeys.KRAVTYPE_ERROR]!!) {
                        shouldHaveSize(6)
                        filter { it.contains(ErrorMessages.KRAVTYPE_DOES_NOT_EXIST) }.shouldHaveSize(6)
                    }

                    with(capturedSendAlertMessages[ErrorKeys.VEDTAKSDATO_ERROR]!!) {
                        shouldHaveSize(1)
                        filter { it.contains(ErrorMessages.VEDTAKSDATO_WRONG_FORMAT) }.shouldHaveSize(1)
                    }

                    with(capturedSendAlertMessages[ErrorKeys.REFERANSENUMMERGAMMELSAK_ERROR]!!) {
                        shouldHaveSize(1)
                        filter { it.contains(ErrorMessages.REFERANSENUMMERGAMMELSAK_WRONG_FORMAT) }.shouldHaveSize(1)
                    }
                    with(capturedSendAlertMessages[ErrorKeys.SAKSNUMMER_ERROR]!!) {
                        shouldHaveSize(1)
                        filter { it.contains(ErrorMessages.SAKSNUMMER_WRONG_FORMAT) }.shouldHaveSize(1)
                    }
                }
            }
            When("Alert sendes") {
                val sendAlertFileNameSlot = slot<String>()
                val sendAlertMessagesSlot = slot<Map<String, List<String>>>()

                coVerify(exactly = 1) {
                    slackClientSpy.sendMessage(any<String>(), capture(sendAlertFileNameSlot), capture(sendAlertMessagesSlot), any<List<TaggablePeople>>(), any())
                }
                sendAlertFileNameSlot.captured shouldBe fileNameOnSftp
                val capturedErrorMessages = sendAlertMessagesSlot.captured
                Then("Skal de 6 like feilmeldingene aggregeres til én") {
                    capturedErrorMessages.shouldHaveSize(4)
                    capturedErrorMessages.keys.filter { it.contains(ErrorKeys.KRAVTYPE_ERROR) }.shouldHaveSize(1)
                }
                Then("Skal de 3 ulike feilmeldingene ikke aggregeres") {
                    capturedErrorMessages.keys.filter { it.contains(ErrorKeys.VEDTAKSDATO_ERROR) }.shouldHaveSize(1)
                    capturedErrorMessages.keys.filter { it.contains(ErrorKeys.REFERANSENUMMERGAMMELSAK_ERROR) }.shouldHaveSize(1)
                    capturedErrorMessages.keys.filter { it.contains(ErrorKeys.SAKSNUMMER_ERROR) }.shouldHaveSize(1)
                }
            }
        }
    })
