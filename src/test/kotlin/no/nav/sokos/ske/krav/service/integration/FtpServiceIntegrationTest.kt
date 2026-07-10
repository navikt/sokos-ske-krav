package no.nav.sokos.ske.krav.service.integration

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.mockk.clearMocks
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify

import no.nav.sokos.ske.krav.config.SftpConfig
import no.nav.sokos.ske.krav.dto.slack.ErrorDetails
import no.nav.sokos.ske.krav.listener.DBListener
import no.nav.sokos.ske.krav.listener.DBListener.clearDB
import no.nav.sokos.ske.krav.listener.DBListener.dataSource
import no.nav.sokos.ske.krav.listener.DBListener.filvalideringsFeilRepository
import no.nav.sokos.ske.krav.listener.SftpListener
import no.nav.sokos.ske.krav.service.Directories
import no.nav.sokos.ske.krav.service.FtpService
import no.nav.sokos.ske.krav.service.SlackService
import no.nav.sokos.ske.krav.util.getAllValideringsFeil
import no.nav.sokos.ske.krav.util.isGivenTest
import no.nav.sokos.ske.krav.util.shouldBe
import no.nav.sokos.ske.krav.util.transaction
import no.nav.sokos.ske.krav.validation.ErrorCategory
import no.nav.sokos.ske.krav.validation.ErrorCategory.FEIL_I_VALIDERING_AV_FIL
import no.nav.sokos.ske.krav.validation.ErrorKeys
import no.nav.sokos.ske.krav.validation.FileValidator

private const val FILE_OK = "AllValideringOk.txt"

internal class FtpServiceIntegrationTest :
    BehaviorSpec({
        extensions(SftpListener, DBListener)
        val slackService =
            mockk<SlackService> {
                justRun { addErrors(any<String>(), any<ErrorCategory>(), any<List<ErrorDetails>>()) }
                coJustRun { sendErrors() }
            }

        val ftpService =
            FtpService(
                dataSource = dataSource,
                sftpConfig = SftpConfig(SftpListener.sftpProperties),
                fileValidator = FileValidator(),
                filValideringsfeilRepository = filvalideringsFeilRepository,
                slackService = slackService,
            )

        fun filenameWithPath(filename: String) = "validering/filvalidering/$filename"

        beforeContainer { testCase ->
            if (testCase.isGivenTest()) {
                clearAllDirectories()
                clearDB()
            }
        }

        afterContainer { (testCase, _) ->
            if (testCase.isGivenTest()) {
                clearMocks(slackService, answers = false)
            }
        }

        afterSpec {
            clearDB()
            clearAllDirectories()
        }

        Given("Det finnes ingen fil i \"inbound\" på FTP-serveren") {
            When("getValidatedFiles kalles") {
                val validatedFiles = ftpService.getValidatedFiles()
                Then("Skal en tom liste returneres") {
                    validatedFiles.shouldBeEmpty()
                }

                And("Ingen feil skal lagres i database") {
                    dataSource.transaction { session ->
                        filvalideringsFeilRepository.getAllValideringsFeil(session).shouldBeEmpty()
                    }
                }

                And("Alert skal ikke sendes") {
                    verify(exactly = 0) { slackService.addErrors(any<String>(), any<ErrorCategory>(), any<List<ErrorDetails>>()) }
                    coVerify(exactly = 0) { slackService.sendErrors() }
                }
            }
        }

        Given("Det finnes en OK fil i \"inbound\" på FTP-serveren") {
            SftpListener.putFile(FILE_OK, Directories.INBOUND)
            When("Filen valideres") {
                val validatedFiles = ftpService.getValidatedFiles()
                Then("Skal en liste med én FtpFil objekt med alle kravLinjene returneres") {
                    validatedFiles shouldHaveSize 1
                    validatedFiles.single().should { file ->
                        file.name shouldBe FILE_OK
                        file.kravLinjer shouldHaveSize 101
                    }
                }
                And("Ingen feil skal lagres i database") {
                    dataSource.transaction { session ->
                        filvalideringsFeilRepository.getAllValideringsFeil(session).shouldBeEmpty()
                    }
                }
                And("Alert skal ikke sendes") {
                    verify(exactly = 0) { slackService.addErrors(any<String>(), any<ErrorCategory>(), any<List<ErrorDetails>>()) }
                    coVerify(exactly = 0) { slackService.sendErrors() }
                }
                And("Filen skal forbli i \"inbound\"") {
                    ftpService.listFiles(Directories.INBOUND).shouldContainExactly(FILE_OK)
                    ftpService.listFiles(Directories.FAILED).shouldBeEmpty()
                }
            }
        }

        Given("Det finnes en fil som har feil antall linjer i kontroll-linjen i \"inbound\" på FTP-serveren") {
            val filename = "FeilAntallKrav.txt"
            SftpListener.putFile(filenameWithPath(filename), Directories.INBOUND)

            When("Filen valideres") {
                val validatedFiles = ftpService.getValidatedFiles()
                Then("Skal en tom liste returneres") {
                    validatedFiles.shouldBeEmpty()
                }

                And("Feilen skal lagres i database") {
                    val filValideringsfeil =
                        dataSource.transaction { session ->
                            filvalideringsFeilRepository.getAllValideringsFeil(session)
                        }

                    filValideringsfeil shouldHaveSize 1
                    filValideringsfeil.single().should { feil ->
                        feil.filnavn shouldBe filename
                        feil.feilmelding shouldContain ErrorKeys.FEIL_I_ANTALL.value
                        feil.feilmelding shouldNotContain ErrorKeys.FEIL_I_SUM.value
                        feil.feilmelding shouldNotContain ErrorKeys.FEIL_I_DATO.value
                    }
                }

                And("Alert skal sendes til slack") {
                    val sendAlertFilenameSlot = slot<String>()
                    val sendAlertHeaderSlot = slot<ErrorCategory>()
                    val sendAlertMessagesSlot = slot<List<ErrorDetails>>()
                    verify(exactly = 1) {
                        slackService.addErrors(capture(sendAlertFilenameSlot), capture(sendAlertHeaderSlot), capture(sendAlertMessagesSlot))
                    }

                    sendAlertFilenameSlot.captured shouldBe filename
                    sendAlertHeaderSlot.captured shouldBe ErrorCategory.FEIL_I_VALIDERING_AV_FIL
                    with(sendAlertMessagesSlot.captured) {
                        shouldHaveSize(1)
                        single().header shouldBe ErrorKeys.FEIL_I_ANTALL
                    }

                    coVerify(exactly = 1) { slackService.sendErrors() }
                }

                And("Filen skal flyttes til \"inbound\\feilfiler\"") {
                    ftpService.listFiles(Directories.INBOUND).shouldBeEmpty()
                    ftpService.listFiles(Directories.FAILED).shouldContainExactly(filename)
                }
            }
        }

        Given("Det finnes en fil som har feil sum i kontroll-linjen i \"inbound\" på FTP-serveren") {
            val filename = "FeilSum.txt"
            SftpListener.putFile(filenameWithPath(filename), Directories.INBOUND)
            When("Filen valideres") {
                val validatedFiles = ftpService.getValidatedFiles()

                Then("Skal en tom liste returneres") {
                    validatedFiles.shouldBeEmpty()
                }

                And("Feilen skal lagres i database") {
                    val filValideringsfeil =
                        dataSource.transaction { session ->
                            filvalideringsFeilRepository.getAllValideringsFeil(session)
                        }
                    filValideringsfeil shouldHaveSize 1
                    filValideringsfeil.single().should { fil ->
                        fil.filnavn shouldBe filename
                        fil.feilmelding shouldContain ErrorKeys.FEIL_I_SUM.value
                        fil.feilmelding shouldNotContain ErrorKeys.FEIL_I_ANTALL.value
                        fil.feilmelding shouldNotContain ErrorKeys.FEIL_I_DATO.value
                    }
                }

                And("Alert skal sendes til slack") {
                    val sendAlertFilenameslot = slot<String>()
                    val sendAlertHeaderSlot = slot<ErrorCategory>()
                    val sendAlertMessagesSlot = slot<List<ErrorDetails>>()

                    verify(exactly = 1) {
                        slackService.addErrors(capture(sendAlertFilenameslot), capture(sendAlertHeaderSlot), capture(sendAlertMessagesSlot))
                    }
                    sendAlertFilenameslot.captured shouldBe filename
                    sendAlertHeaderSlot.captured shouldBe ErrorCategory.FEIL_I_VALIDERING_AV_FIL
                    with(sendAlertMessagesSlot.captured) {
                        shouldHaveSize(1)
                        single().header shouldBe ErrorKeys.FEIL_I_SUM
                    }

                    coVerify(exactly = 1) { slackService.sendErrors() }
                }

                And("Filen skal flyttes til \"inbound\\feilfiler\"") {
                    ftpService.listFiles(Directories.INBOUND).shouldBeEmpty()
                    ftpService.listFiles(Directories.FAILED).shouldContainExactly(filename)
                }
            }
        }

        Given("Det finnes en fil som har forskjellige datoer i kontroll-linjene i \"inbound\" på FTP-serveren") {
            val filename = "FeilUtbetalDato.txt"
            SftpListener.putFile(filenameWithPath(filename), Directories.INBOUND)

            When("Filen valideres") {
                val validatedFiles = ftpService.getValidatedFiles()

                Then("Skal en tom liste returneres") {
                    validatedFiles.shouldBeEmpty()
                }

                And("Feilen skal lagres i database") {
                    val filValideringsfeil =
                        dataSource.transaction { session ->
                            filvalideringsFeilRepository.getAllValideringsFeil(session)
                        }

                    filValideringsfeil shouldHaveSize 1
                    filValideringsfeil.single().should { feil ->
                        feil.filnavn shouldBe filename
                        feil.feilmelding shouldContain ErrorKeys.FEIL_I_DATO.value
                        feil.feilmelding shouldNotContain ErrorKeys.FEIL_I_ANTALL.value
                        feil.feilmelding shouldNotContain ErrorKeys.FEIL_I_SUM.value
                    }
                }

                And("Alert skal sendes til slack") {
                    val sendAlertFilenameSlot = slot<String>()
                    val sendAlertHeaderSlot = slot<ErrorCategory>()
                    val sendAlertMessagesSlot = slot<List<ErrorDetails>>()

                    verify(exactly = 1) {
                        slackService.addErrors(capture(sendAlertFilenameSlot), capture(sendAlertHeaderSlot), capture(sendAlertMessagesSlot))
                    }
                    sendAlertFilenameSlot.captured shouldBe filename
                    sendAlertHeaderSlot.captured shouldBe FEIL_I_VALIDERING_AV_FIL
                    with(sendAlertMessagesSlot.captured) {
                        shouldHaveSize(1)
                        single().header shouldBe ErrorKeys.FEIL_I_DATO
                    }

                    coVerify(exactly = 1) { slackService.sendErrors() }
                }

                And("Filen skal flyttes til \"inbound\\feilfiler\"") {
                    ftpService.listFiles(Directories.INBOUND).shouldBeEmpty()
                    ftpService.listFiles(Directories.FAILED).shouldContainExactly(filename)
                }
            }
        }

        Given("listFiles kalles") {
            listOf(Directories.INBOUND, Directories.OUTBOUND, Directories.FAILED).forEach { directory ->
                When("Directory er ${directory.name}") {
                    Then("Skal listFiles returnere filer i ${directory.name}") {
                        SftpListener.putFiles(listOf(FILE_OK), directory)
                        val filesInDir = ftpService.listFiles(directory)
                        filesInDir.size shouldBe 1
                        filesInDir shouldContain FILE_OK
                    }
                }
            }
        }
    })

private fun clearAllDirectories() {
    Directories.entries.forEach { directory ->
        SftpListener.clearDirectory(directory)
    }
}
