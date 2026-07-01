package no.nav.sokos.ske.krav.validation

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.inspectors.forOne
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.mockk.coVerify
import io.mockk.slot
import io.mockk.spyk

import no.nav.sokos.ske.krav.client.SlackClient
import no.nav.sokos.ske.krav.config.SftpConfig
import no.nav.sokos.ske.krav.dto.slack.ErrorDetails
import no.nav.sokos.ske.krav.listener.DBListener
import no.nav.sokos.ske.krav.listener.DBListener.dataSource
import no.nav.sokos.ske.krav.listener.DBListener.filvalideringsFeilRepository
import no.nav.sokos.ske.krav.listener.SftpListener
import no.nav.sokos.ske.krav.service.Directories
import no.nav.sokos.ske.krav.service.FtpService
import no.nav.sokos.ske.krav.service.SlackService
import no.nav.sokos.ske.krav.util.getFilValideringsFeilForFil
import no.nav.sokos.ske.krav.util.http.MockHttpClient
import no.nav.sokos.ske.krav.util.shouldBe
import no.nav.sokos.ske.krav.util.transaction
import no.nav.sokos.ske.krav.validation.ErrorCategory.FEIL_I_VALIDERING_AV_FIL

// TODO: Merge with FtpServiceIntegrationTest
internal class FileValidatorIntegrationTest :
    BehaviorSpec({
        extensions(SftpListener, DBListener)

        fun setupSlackService(): SlackService {
            val slackClientSpy = spyk(SlackClient(client = MockHttpClient.slackClient))
            return spyk(SlackService(slackClientSpy), recordPrivateCalls = true)
        }

        fun setupFtpService(slackServiceSpy: SlackService): FtpService =
            FtpService(
                dataSource = dataSource,
                sftpConfig = SftpConfig(SftpListener.sftpProperties),
                fileValidator = FileValidator(),
                filValideringsfeilRepository = filvalideringsFeilRepository,
                slackService = slackServiceSpy,
            )

        Given("Fil er OK") {
            val slackServiceSpy = setupSlackService()
            val ftpService = setupFtpService(slackServiceSpy)
            val fileName = "AllValideringOk.txt"
            SftpListener.putFiles(listOf(fileName), Directories.INBOUND)

            When("Filen valideres") {
                ftpService.getValidatedFiles()

                Then("Skal ingen feil lagres i database") {
                    dataSource.transaction { session ->
                        filvalideringsFeilRepository.getFilValideringsFeilForFil(session, fileName).shouldBeEmpty()
                    }
                }

                And("Alert skal ikke sendes") {
                    coVerify(exactly = 0) {
                        slackServiceSpy.addErrors(any<String>(), any<ErrorCategory>(), any<List<ErrorDetails>>())
                    }
                }
            }
        }
        Given("En fil har feil antall linjer i kontroll-linjen") {
            val slackServiceSpy = setupSlackService()
            val ftpService = setupFtpService(slackServiceSpy)

            val fileName = "validering/filvalidering/FeilAntallKrav.txt"
            val fileNameOnSftp = fileName.substringAfterLast("/")
            SftpListener.putFiles(listOf(fileName), Directories.INBOUND)

            When("Filen valideres") {
                ftpService.getValidatedFiles()

                Then("Skal feilen lagres i database") {
                    val filValideringsfeil =
                        dataSource.transaction { session ->
                            filvalideringsFeilRepository.getFilValideringsFeilForFil(session, fileNameOnSftp)
                        }
                    filValideringsfeil.shouldHaveSize(1)

                    with(filValideringsfeil.first()) {
                        filnavn shouldBe fileNameOnSftp
                        feilmelding shouldContain ErrorKeys.FEIL_I_ANTALL.value
                        feilmelding shouldNotContain ErrorKeys.FEIL_I_SUM.value
                        feilmelding shouldNotContain ErrorKeys.FEIL_I_DATO.value
                    }
                }
                And("Alert skal sendes til slack") {
                    val sendAlertFilenameSlot = slot<String>()
                    val sendAlertHeaderSlot = slot<ErrorCategory>()
                    val sendAlertMessagesSlot = slot<List<ErrorDetails>>()

                    coVerify(exactly = 1) {
                        slackServiceSpy.addErrors(capture(sendAlertFilenameSlot), capture(sendAlertHeaderSlot), capture(sendAlertMessagesSlot))
                    }
                    sendAlertFilenameSlot.captured shouldBe fileNameOnSftp
                    sendAlertHeaderSlot.captured shouldBe FEIL_I_VALIDERING_AV_FIL
                    val capturedSendAlertMessages: List<ErrorDetails> = sendAlertMessagesSlot.captured
                    capturedSendAlertMessages.shouldHaveSize(1)
                    capturedSendAlertMessages.first().header shouldBe ErrorKeys.FEIL_I_ANTALL
                }
            }
        }

        Given("En fil har feil sum i kontroll-linjen") {
            val slackServiceSpy = setupSlackService()
            val ftpService = setupFtpService(slackServiceSpy)
            val fileName = "validering/filvalidering/FeilSum.txt"
            val fileNameOnSftp = fileName.substringAfterLast("/")
            SftpListener.putFiles(listOf(fileName), Directories.INBOUND)

            When("Filen valideres") {
                ftpService.getValidatedFiles()

                Then("Skal feilen lagres i database ") {
                    val filValideringsfeils =
                        dataSource.transaction { session ->
                            filvalideringsFeilRepository.getFilValideringsFeilForFil(session, fileNameOnSftp)
                        }

                    filValideringsfeils.shouldHaveSize(1)
                    with(filValideringsfeils.first()) {
                        filnavn shouldBe fileNameOnSftp
                        feilmelding shouldContain ErrorKeys.FEIL_I_SUM.value
                        feilmelding shouldNotContain ErrorKeys.FEIL_I_ANTALL.value
                        feilmelding shouldNotContain ErrorKeys.FEIL_I_DATO.value
                    }
                }
                And("Alert skal sendes til slack") {
                    val sendAlertFilenameSlot = slot<String>()
                    val sendAlertHeaderSlot = slot<ErrorCategory>()
                    val sendAlertMessagesSlot = slot<List<ErrorDetails>>()

                    coVerify(exactly = 1) {
                        slackServiceSpy.addErrors(capture(sendAlertFilenameSlot), capture(sendAlertHeaderSlot), capture(sendAlertMessagesSlot))
                    }
                    sendAlertFilenameSlot.captured shouldBe fileNameOnSftp
                    sendAlertHeaderSlot.captured shouldBe FEIL_I_VALIDERING_AV_FIL
                    with(sendAlertMessagesSlot.captured) {
                        shouldHaveSize(1)
                        first().header shouldBe ErrorKeys.FEIL_I_SUM
                    }
                }
            }
        }

        Given("En fil har forskjellige datoer i kontroll-linjene") {
            val slackServiceSpy = setupSlackService()
            val ftpService = setupFtpService(slackServiceSpy)
            val fileName = "validering/filvalidering/FeilUtbetalDato.txt"
            val fileNameOnSftp = fileName.substringAfterLast("/")
            SftpListener.putFiles(listOf(fileName), Directories.INBOUND)

            When("Filen valideres") {
                ftpService.getValidatedFiles()

                Then("Skal feilen lagres i database ") {
                    val filValideringsfeils =
                        dataSource.transaction { session ->
                            filvalideringsFeilRepository.getFilValideringsFeilForFil(session, fileNameOnSftp)
                        }

                    filValideringsfeils.shouldHaveSize(1)
                    with(filValideringsfeils.first()) {
                        filnavn shouldBe fileNameOnSftp
                        feilmelding shouldContain ErrorKeys.FEIL_I_DATO.value
                        feilmelding shouldNotContain ErrorKeys.FEIL_I_SUM.value
                        feilmelding shouldNotContain ErrorKeys.FEIL_I_ANTALL.value
                    }
                }
                And("Alert skal sendes til slack") {
                    val sendAlertFilenameSlot = slot<String>()
                    val sendAlertHeaderSlot = slot<ErrorCategory>()
                    val sendAlertMessagesSlot = slot<List<ErrorDetails>>()

                    coVerify(exactly = 1) {
                        slackServiceSpy.addErrors(capture(sendAlertFilenameSlot), capture(sendAlertHeaderSlot), capture(sendAlertMessagesSlot))
                    }
                    sendAlertFilenameSlot.captured shouldBe fileNameOnSftp
                    sendAlertHeaderSlot.captured shouldBe FEIL_I_VALIDERING_AV_FIL
                    val capturedSendAlertMessages: List<ErrorDetails> = sendAlertMessagesSlot.captured
                    capturedSendAlertMessages.shouldHaveSize(1)
                    capturedSendAlertMessages.first().header shouldBe ErrorKeys.FEIL_I_DATO
                }
            }
        }

        Given("En fil har alle typer feil") {
            val slackServiceSpy = setupSlackService()
            val ftpService = setupFtpService(slackServiceSpy)
            val fileName = "validering/filvalidering/AlleTyperFeil.txt"
            val fileNameOnSftp = fileName.substringAfterLast("/")
            SftpListener.putFiles(listOf(fileName), Directories.INBOUND)

            When("Filen valideres") {
                ftpService.getValidatedFiles()

                Then("Skal feilene lagres i database ") {
                    val filValideringsfeils =
                        dataSource.transaction { session ->
                            filvalideringsFeilRepository.getFilValideringsFeilForFil(session, fileNameOnSftp)
                        }

                    with(filValideringsfeils) {
                        shouldHaveSize(4)
                        count { it.filnavn == fileNameOnSftp } shouldBe 4
                        count { it.feilmelding.contains(ErrorKeys.FEIL_I_DATO.value) } shouldBe 1
                        count { it.feilmelding.contains(ErrorKeys.FEIL_I_SUM.value) } shouldBe 1
                        count { it.feilmelding.contains(ErrorKeys.FEIL_I_ANTALL.value) } shouldBe 1
                        count { it.feilmelding.contains(ErrorKeys.FAGSYSTEMID_MANGLER.value) } shouldBe 1
                    }
                }
                And("Alert skal sendes til slack") {
                    val sendAlertFilenameSlot = slot<String>()
                    val sendAlertHeaderSlot = slot<ErrorCategory>()
                    val sendAlertMessagesSlot = slot<List<ErrorDetails>>()

                    coVerify(exactly = 1) {
                        slackServiceSpy.addErrors(capture(sendAlertFilenameSlot), capture(sendAlertHeaderSlot), capture(sendAlertMessagesSlot))
                    }
                    sendAlertFilenameSlot.captured shouldBe fileNameOnSftp
                    sendAlertHeaderSlot.captured shouldBe FEIL_I_VALIDERING_AV_FIL
                    with(sendAlertMessagesSlot.captured) {
                        shouldHaveSize(4)
                        forOne { it.header shouldBe ErrorKeys.FEIL_I_DATO }
                        forOne { it.header shouldBe ErrorKeys.FEIL_I_SUM }
                        forOne { it.header shouldBe ErrorKeys.FEIL_I_ANTALL }
                        forOne { it.header shouldBe ErrorKeys.FAGSYSTEMID_MANGLER }
                    }
                }
            }
        }
        Given("Fil fra Arena - mangler alltid fagsystemID") {
            val slackServiceSpy = setupSlackService()
            val ftpService = setupFtpService(slackServiceSpy)
            val fileName = "innsender/ArenaFil.txt"
            val fileNameOnSftp = fileName.substringAfterLast("/")
            SftpListener.putFiles(listOf(fileName), Directories.INBOUND)

            When("Filen valideres") {
                ftpService.getValidatedFiles()

                Then("Skal ingen feil lagres i database") {
                    dataSource.transaction { session ->
                        filvalideringsFeilRepository.getFilValideringsFeilForFil(session, fileNameOnSftp).shouldBeEmpty()
                    }
                }

                And("Alert skal ikke sendes") {
                    coVerify(exactly = 0) {
                        slackServiceSpy.addErrors(any<String>(), any<ErrorCategory>(), any<List<ErrorDetails>>())
                    }
                }
            }
        }

        Given("Fil fra Pesys - mangler alltid fagsystemID") {
            val slackServiceSpy = setupSlackService()
            val ftpService = setupFtpService(slackServiceSpy)
            val fileName = "innsender/PesysFil.txt"
            val fileNameOnSftp = fileName.substringAfterLast("/")
            SftpListener.putFiles(listOf(fileName), Directories.INBOUND)

            When("Filen valideres") {
                ftpService.getValidatedFiles()

                Then("Skal ingen feil lagres i database") {
                    dataSource.transaction { session ->
                        filvalideringsFeilRepository.getFilValideringsFeilForFil(session, fileNameOnSftp).shouldBeEmpty()
                    }
                }

                And("Alert skal ikke sendes") {
                    coVerify(exactly = 0) {
                        slackServiceSpy.addErrors(any<String>(), any<ErrorCategory>(), any<List<ErrorDetails>>())
                    }
                }
            }
        }

        Given("Fil fra Infotrygd - mangler alltid fagsystemID") {
            val slackServiceSpy = setupSlackService()
            val ftpService = setupFtpService(slackServiceSpy)
            val fileName = "innsender/InfotrygdFil.txt"
            val fileNameOnSftp = fileName.substringAfterLast("/")
            SftpListener.putFiles(listOf(fileName), Directories.INBOUND)

            When("Filen valideres") {
                ftpService.getValidatedFiles()

                Then("Skal ingen feil lagres i database") {
                    dataSource.transaction { session ->
                        filvalideringsFeilRepository.getFilValideringsFeilForFil(session, fileNameOnSftp).shouldBeEmpty()
                    }
                }

                And("Alert skal ikke sendes") {
                    coVerify(exactly = 0) {
                        slackServiceSpy.addErrors(any<String>(), any<ErrorCategory>(), any<List<ErrorDetails>>())
                    }
                }
            }
        }

        Given("Fil med tilleggsfrist") {
            val slackServiceSpy = setupSlackService()
            val ftpService = setupFtpService(slackServiceSpy)
            val fileName = "krav/MedTilleggsfrist.txt"
            val fileNameOnSftp = fileName.substringAfterLast("/")
            SftpListener.putFiles(listOf(fileName), Directories.INBOUND)

            When("Filen valideres") {
                ftpService.getValidatedFiles()

                Then("Skal ingen feil lagres i database") {
                    dataSource.transaction { session ->
                        filvalideringsFeilRepository.getFilValideringsFeilForFil(session, fileNameOnSftp).shouldBeEmpty()
                    }
                }

                And("Alert skal ikke sendes") {
                    coVerify(exactly = 0) {
                        slackServiceSpy.addErrors(any<String>(), any<ErrorCategory>(), any<List<ErrorDetails>>())
                    }
                }
            }
        }
    })
