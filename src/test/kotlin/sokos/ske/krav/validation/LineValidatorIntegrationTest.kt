package sokos.ske.krav.validation

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import io.mockk.spyk
import sokos.ske.krav.client.SlackClient
import sokos.ske.krav.config.SftpConfig
import sokos.ske.krav.database.models.ValideringsfeilTable
import sokos.ske.krav.database.toValideringsfeil
import sokos.ske.krav.service.DatabaseService
import sokos.ske.krav.service.Directories
import sokos.ske.krav.service.FtpService
import sokos.ske.krav.util.MockHttpClient
import sokos.ske.krav.util.SftpListener
import sokos.ske.krav.util.TestContainer
import sokos.ske.krav.validation.LineValidationRules.ErrorKeys
import sokos.ske.krav.validation.LineValidationRules.ErrorMessages
import sokos.ske.krav.validation.LineValidationRules.errorDate
import java.sql.Connection

internal class LineValidatorIntegrationTest :
    BehaviorSpec({
        extensions(SftpListener)

        Given("Alle linjer er ok") {
            val dataSource = TestContainer().dataSource

            val dbService = DatabaseService(dataSource)
            val slackClient = spyk(SlackClient(client = MockHttpClient().getSlackClient()))
            val lineValidatorSpy = spyk(LineValidator(slackClient), recordPrivateCalls = true)
            val ftpService: FtpService by lazy {
                FtpService(SftpConfig(SftpListener.sftpProperties), FileValidator(slackClient), databaseService = mockk<DatabaseService>(relaxed = true))
            }
            val fileName = "AltOkFil.txt"
            SftpListener.putFiles(listOf(fileName), Directories.INBOUND)

            val ftpFil = ftpService.getValidatedFiles().first { it.name == fileName }

            When("Linjer valideres") {
                lineValidatorSpy.validateNewLines(ftpFil, dbService)

                Then("Skal ingen feil lagres i database") {
                    dataSource.connection.getValideringsFeil(fileName).size shouldBe 0
                }
                And("Alert skal ikke sendes") {
                    coVerify(exactly = 0) {
                        lineValidatorSpy["sendAlert"](any<String>(), any<Map<String, List<String>>>())
                    }
                }
            }
        }

        Given("1 linje har 1 feil") {
            val dataSource = TestContainer().dataSource
            val dbService = DatabaseService(dataSource)
            val slackClientSpy = spyk(SlackClient(client = MockHttpClient().getSlackClient()))
            val lineValidatorSpy = spyk(LineValidator(slackClientSpy), recordPrivateCalls = true)
            val ftpService: FtpService by lazy {
                FtpService(SftpConfig(SftpListener.sftpProperties), FileValidator(slackClientSpy), databaseService = mockk<DatabaseService>(relaxed = true))
            }
            val fileName = "1LinjeHarFeilKravtype.txt"
            SftpListener.putFiles(listOf(fileName), Directories.INBOUND)

            val ftpFil = ftpService.getValidatedFiles().first { it.name == fileName }

            When("Linjer valideres") {

                lineValidatorSpy.validateNewLines(ftpFil, dbService)

                Then("Skal én feil lagres i database") {
                    with(dataSource.connection.getValideringsFeil(fileName)) {
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
                And(" 1 Feilmelding skal dannes for alert") {
                    val sendAlertFilenameSlot = slot<String>()
                    val sendAlertMessagesSlot = slot<Map<String, List<String>>>()

                    coVerify(exactly = 1) {
                        lineValidatorSpy["sendAlert"](capture(sendAlertFilenameSlot), capture(sendAlertMessagesSlot))
                    }

                    sendAlertFilenameSlot.captured shouldBe fileName
                    val capturedSendAlertMessages: Map<String, List<String>> = sendAlertMessagesSlot.captured
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

                    And("Feilmeldinger skal ikke aggregeres") {
                        val sendLinjevalideringsMeldingFilenameSlot = slot<String>()
                        val sendLinjevalideringsMeldingMessagesSlot = slot<Map<String, List<String>>>()

                        coVerify(exactly = 1) {
                            slackClientSpy["sendLinjevalideringsMelding"](capture(sendLinjevalideringsMeldingFilenameSlot), capture(sendLinjevalideringsMeldingMessagesSlot))
                        }
                        sendLinjevalideringsMeldingFilenameSlot.captured shouldBe fileName

                        val capturedErrorMessages = sendLinjevalideringsMeldingMessagesSlot.captured
                        capturedErrorMessages shouldBe capturedSendAlertMessages
                    }
                }
            }
        }

        Given("1 linje har 3 forskjellige feil") {
            val dataSource = TestContainer().dataSource
            val dbService = DatabaseService(dataSource)
            val slackClientSpy = spyk(SlackClient(client = MockHttpClient().getSlackClient()))
            val lineValidatorSpy = spyk(LineValidator(slackClientSpy), recordPrivateCalls = true)
            val ftpService: FtpService by lazy {
                FtpService(SftpConfig(SftpListener.sftpProperties), FileValidator(slackClientSpy), databaseService = mockk<DatabaseService>(relaxed = true))
            }

            val fileName = "1LinjeHarFeilSaksnummer_OgVedtaksdato_OgKravtype.txt"
            SftpListener.putFiles(listOf(fileName), Directories.INBOUND)

            val ftpFil = ftpService.getValidatedFiles().first { it.name == fileName }

            When("Linjer valideres") {
                lineValidatorSpy.validateNewLines(ftpFil, dbService)

                Then("Skal 3 feil lagres som én feilmelding i database") {
                    with(dataSource.connection.getValideringsFeil(fileName)) {
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
                And("3 feilmeldinger skal dannes for alert") {
                    val sendAlertFilenameSlot = slot<String>()
                    val sendAlertMessagesSlot = slot<Map<String, List<String>>>()

                    coVerify(exactly = 1) {
                        lineValidatorSpy["sendAlert"](capture(sendAlertFilenameSlot), capture(sendAlertMessagesSlot))
                    }

                    sendAlertFilenameSlot.captured shouldBe fileName
                    val capturedSendAlertMessages: Map<String, List<String>> = sendAlertMessagesSlot.captured
                    capturedSendAlertMessages.size shouldBe 3

                    capturedSendAlertMessages[ErrorKeys.UTBETALINGSDATO_ERROR] shouldBe null
                    capturedSendAlertMessages[ErrorKeys.PERIODE_ERROR] shouldBe null
                    capturedSendAlertMessages[ErrorKeys.REFERANSENUMMERGAMMELSAK_ERROR] shouldBe null

                    capturedSendAlertMessages[ErrorKeys.VEDTAKSDATO_ERROR] shouldNotBe null
                    capturedSendAlertMessages[ErrorKeys.SAKSNUMMER_ERROR] shouldNotBe null
                    capturedSendAlertMessages[ErrorKeys.KRAVTYPE_ERROR] shouldNotBe null

                    with(capturedSendAlertMessages[ErrorKeys.VEDTAKSDATO_ERROR]!!) {
                        size shouldBe 1
                        first() shouldContain ErrorMessages.VEDTAKSDATO_IS_IN_FUTURE
                    }
                    with(capturedSendAlertMessages[ErrorKeys.SAKSNUMMER_ERROR]!!) {
                        size shouldBe 1
                        first() shouldContain ErrorMessages.SAKSNUMMER_WRONG_FORMAT
                    }

                    with(capturedSendAlertMessages[ErrorKeys.KRAVTYPE_ERROR]!!) {
                        size shouldBe 1
                        first() shouldContain ErrorMessages.KRAVTYPE_DOES_NOT_EXIST
                    }
                    And("Feilmeldinger skal ikke aggregeres") {
                        val sendLinjevalideringsMeldingFilenameSlot = slot<String>()
                        val sendLinjevalideringsMeldingMessagesSlot = slot<Map<String, List<String>>>()

                        coVerify(exactly = 1) {
                            slackClientSpy["sendLinjevalideringsMelding"](capture(sendLinjevalideringsMeldingFilenameSlot), capture(sendLinjevalideringsMeldingMessagesSlot))
                        }
                        sendLinjevalideringsMeldingFilenameSlot.captured shouldBe fileName

                        val capturedErrorMessages = sendLinjevalideringsMeldingMessagesSlot.captured
                        capturedErrorMessages shouldBe capturedSendAlertMessages
                    }
                }
            }
        }

        Given("6 linjer har samme type feil") {
            val fileName = "6LinjerHarSammeTypeFeil.txt"
            SftpListener.putFiles(listOf(fileName), Directories.INBOUND)

            val ftpService: FtpService by lazy {
                FtpService(SftpConfig(SftpListener.sftpProperties), FileValidator(SlackClient(client = MockHttpClient().getSlackClient())), databaseService = mockk<DatabaseService>(relaxed = true))
            }
            val ftpFil = ftpService.getValidatedFiles().first { it.name == fileName }
            ftpFil.kravLinjer.size shouldBe 10

            When("Linjer valideres") {
                val dataSource = TestContainer().dataSource
                val dbService = DatabaseService(dataSource)

                val slackClientSpy = spyk(SlackClient(client = MockHttpClient().getSlackClient()))
                val lineValidatorSpy = spyk(LineValidator(slackClientSpy), recordPrivateCalls = true)

                lineValidatorSpy.validateNewLines(ftpFil, dbService)

                Then("Skal 6 feil lagres i database") {
                    with(dataSource.connection.getValideringsFeil(fileName)) {
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
                And("6 feilmeldinger av samme type skal dannes for alert") {
                    val sendAlertFilenameSlot = slot<String>()
                    val sendAlertMessagesSlot = slot<Map<String, List<String>>>()

                    coVerify(exactly = 1) {
                        lineValidatorSpy["sendAlert"](capture(sendAlertFilenameSlot), capture(sendAlertMessagesSlot))
                    }

                    sendAlertFilenameSlot.captured shouldBe fileName
                    val capturedSendAlertMessages: Map<String, List<String>> = sendAlertMessagesSlot.captured
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

                    When("Alert Lages") {
                        val sendLinjevalideringsMeldingFilenameSlot = slot<String>()
                        val sendLinjevalideringsMeldingMessagesSlot = slot<Map<String, List<String>>>()

                        coVerify(exactly = 1) {
                            slackClientSpy["sendLinjevalideringsMelding"](capture(sendLinjevalideringsMeldingFilenameSlot), capture(sendLinjevalideringsMeldingMessagesSlot))
                        }
                        sendLinjevalideringsMeldingFilenameSlot.captured shouldBe fileName

                        val capturedErrorMessages = sendLinjevalideringsMeldingMessagesSlot.captured
                        Then("Skal feilmeldingene aggregeres") {
                            capturedErrorMessages.size shouldBe 1
                            capturedErrorMessages.keys.first() shouldContain ErrorMessages.KRAVTYPE_DOES_NOT_EXIST
                        }
                    }
                }
            }
            And("3 linjer har ulike feil") {
                val dataSource = TestContainer().dataSource
                val dbService = DatabaseService(dataSource)

                val slackClientSpy = spyk(SlackClient(client = MockHttpClient().getSlackClient()))
                val lineValidatorSpy = spyk(LineValidator(slackClientSpy), recordPrivateCalls = true)

                val ikkeOkKravMedUlikeFeil =
                    ftpFil.kravLinjer.filter { it.kravKode == "MJ AU" }.mapIndexed { index, krav ->
                        when (index) {
                            0 -> krav.copy(saksnummerNav = "saksnummer_ø")
                            1 -> krav.copy(referansenummerGammelSak = "refgammel_ø")
                            2 -> krav.copy(vedtaksDato = errorDate)
                            else -> krav
                        }
                    }

                When("Linjer valideres") {
                    lineValidatorSpy.validateNewLines(ftpFil.copy(kravLinjer = ikkeOkKravMedUlikeFeil), dbService)

                    Then("Skal 6 feil lagres  i database ") {
                        with(dataSource.connection.getValideringsFeil(fileName)) {
                            size shouldBe 6
                            filter { it.feilmelding.contains(ErrorMessages.KRAVTYPE_DOES_NOT_EXIST) }.size shouldBe 6
                            filter { it.feilmelding.contains(ErrorMessages.VEDTAKSDATO_WRONG_FORMAT) }.size shouldBe 1
                            filter { it.feilmelding.contains(ErrorMessages.REFERANSENUMMERGAMMELSAK_WRONG_FORMAT) }.size shouldBe 1
                            filter { it.feilmelding.contains(ErrorMessages.SAKSNUMMER_WRONG_FORMAT) }.size shouldBe 1
                        }
                    }

                    And("9 feilmeldinger skal dannes for alert") {
                        val sendAlertFilenameSlot = slot<String>()
                        val sendAlertMessagesSlot = slot<Map<String, List<String>>>()

                        coVerify(exactly = 1) {
                            lineValidatorSpy["sendAlert"](capture(sendAlertFilenameSlot), capture(sendAlertMessagesSlot))
                        }

                        sendAlertFilenameSlot.captured shouldBe fileName
                        val capturedSendAlertMessages: Map<String, List<String>> = sendAlertMessagesSlot.captured
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

                        When("Alert sendes") {
                            val sendLinjevalideringsMeldingFilenameSlot = slot<String>()
                            val sendLinjevalideringsMeldingMessagesSlot = slot<Map<String, List<String>>>()

                            coVerify(exactly = 1) {
                                slackClientSpy["sendLinjevalideringsMelding"](capture(sendLinjevalideringsMeldingFilenameSlot), capture(sendLinjevalideringsMeldingMessagesSlot))
                            }
                            sendLinjevalideringsMeldingFilenameSlot.captured shouldBe fileName
                            val capturedErrorMessages = sendLinjevalideringsMeldingMessagesSlot.captured
                            Then("Skal de 6 like feilmeldingene aggregeres") {
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
        }
    })

// TODO: Sjekk hvordan valideringsfeil hentes i nye frontenden og om dette finnes
private fun Connection.getValideringsFeil(filNavn: String): List<ValideringsfeilTable> =
    prepareStatement(
        """select * from valideringsfeil where filnavn='$filNavn'""".trimIndent(),
    ).executeQuery()
        .toValideringsfeil()
