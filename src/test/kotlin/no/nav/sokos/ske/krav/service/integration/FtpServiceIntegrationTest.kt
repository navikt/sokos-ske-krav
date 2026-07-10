package no.nav.sokos.ske.krav.service.integration

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.inspectors.forOne
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
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

                And("Alert skal sendes til Slack") {
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

                And("Alert skal sendes til Slack") {
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

                And("Alert skal sendes til Slack") {
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

        Given("Det finnes en fil som har alle typer feil i \"inbound\" på FTP-serveren") {
            val filename = "AlleTyperFeil.txt"
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

                    filValideringsfeil shouldHaveSize 4
                    filValideringsfeil.forOne {
                        it.feilmelding shouldContain ErrorKeys.FEIL_I_DATO.value
                    }
                    filValideringsfeil.forOne {
                        it.feilmelding shouldContain ErrorKeys.FEIL_I_SUM.value
                    }
                    filValideringsfeil.forOne {
                        it.feilmelding shouldContain ErrorKeys.FEIL_I_ANTALL.value
                    }
                    filValideringsfeil.forOne {
                        it.feilmelding shouldContain ErrorKeys.FAGSYSTEMID_MANGLER.value
                    }
                }

                And("Alert skal sendes til Slack") {
                    val sendAlertFilenameSlot = slot<String>()
                    val sendAlertHeaderSlot = slot<ErrorCategory>()
                    val sendAlertMessagesSlot = slot<List<ErrorDetails>>()

                    coVerify(exactly = 1) { slackService.addErrors(capture(sendAlertFilenameSlot), capture(sendAlertHeaderSlot), capture(sendAlertMessagesSlot)) }
                    sendAlertFilenameSlot.captured shouldBe filename
                    sendAlertHeaderSlot.captured shouldBe ErrorCategory.FEIL_I_VALIDERING_AV_FIL
                    with(sendAlertMessagesSlot.captured) {
                        shouldHaveSize(4)
                        forOne { it.header shouldBe ErrorKeys.FEIL_I_DATO }
                        forOne { it.header shouldBe ErrorKeys.FEIL_I_SUM }
                        forOne { it.header shouldBe ErrorKeys.FEIL_I_ANTALL }
                        forOne { it.header shouldBe ErrorKeys.FAGSYSTEMID_MANGLER }
                    }
                    coVerify(exactly = 1) { slackService.sendErrors() }
                }

                And("Filen skal flyttes til \"inbound\\feilfiler\"") {
                    ftpService.listFiles(Directories.INBOUND).shouldBeEmpty()
                    ftpService.listFiles(Directories.FAILED).shouldContainExactly(filename)
                }
            }
        }

        Given("Det finnes tre filer fra Arena, Pesys og Infotrygd som alltid mangler fagsystemID i \"inbound\" på FTP-serveren") {
            fun filenameWithInnsenderPath(filename: String) = "innsender/$filename"

            val filenameArena = "ArenaFil.txt"
            val filenamePesys = "PesysFil.txt"
            val filenameInfotrygd = "InfotrygdFil.txt"
            val files =
                listOf(
                    filenameWithInnsenderPath(filenameArena),
                    filenameWithInnsenderPath(filenamePesys),
                    filenameWithInnsenderPath(filenameInfotrygd),
                )
            SftpListener.putFiles(files, Directories.INBOUND)

            When("Filene valideres") {
                val validatedFiles = ftpService.getValidatedFiles()
                Then("Skal tre FtpFil objekt med alle kravlinjene returneres") {
                    validatedFiles shouldHaveSize 3
                    validatedFiles.forOne { (name, lines) ->
                        name shouldBe filenameArena
                        lines shouldHaveSize 18
                    }
                    validatedFiles.forOne { (name, lines) ->
                        name shouldBe filenamePesys
                        lines shouldHaveSize 3
                    }
                    validatedFiles.forOne { (name, lines) ->
                        name shouldBe filenameInfotrygd
                        lines shouldHaveSize 6
                    }
                }
                And("Ingen feil skal lagres i database") {
                    dataSource.transaction { session ->
                        filvalideringsFeilRepository.getAllValideringsFeil(session).shouldBeEmpty()
                    }
                }
                And("Alert skal ikke sendes til Slack") {
                    verify(exactly = 0) { slackService.addErrors(any(), any(), any()) }
                    coVerify(exactly = 0) { slackService.sendErrors() }
                }
                And("Filene skal forbli i \"inbound\"") {
                    ftpService.listFiles(Directories.INBOUND).shouldContainExactlyInAnyOrder(filenameArena, filenamePesys, filenameInfotrygd)
                    ftpService.listFiles(Directories.FAILED).shouldBeEmpty()
                }
            }
        }

        Given("Det finnes en fil med tillegsrist i \"inbound\" på FTP-serveren") {
            val filename = "MedTilleggsfrist.txt"
            SftpListener.putFile("krav/$filename")

            When("Filen valideres") {
                val validatedFiles = ftpService.getValidatedFiles()
                Then("Skal én FtpFil objekt returneres med alle kravlinjene") {
                    validatedFiles shouldHaveSize 1
                    validatedFiles.single().should { file ->
                        file.name shouldBe filename
                        file.kravLinjer shouldHaveSize 1
                    }
                }
                And("Ingen feil skal lagres i database") {
                    dataSource.transaction { session ->
                        filvalideringsFeilRepository.getAllValideringsFeil(session).shouldBeEmpty()
                    }
                }
                And("Alert skal ikke sendes til Slack") {
                    verify(exactly = 0) { slackService.addErrors(any(), any(), any()) }
                    coVerify(exactly = 0) { slackService.sendErrors() }
                }
                And("Filene skal forbli i \"inbound\"") {
                    ftpService.listFiles(Directories.INBOUND).shouldContainExactly(filename)
                    ftpService.listFiles(Directories.FAILED).shouldBeEmpty()
                }
            }
        }

        Given("Det finnes to valid filer i \"inbound\" på FTP-serveren") {
            val validFile2 = "TiNyeKrav.txt"
            val validFile2Path = "krav/$validFile2"
            SftpListener.putFiles(listOf(FILE_OK, validFile2Path), Directories.INBOUND)

            When("Filene valideres") {
                val validFiles = ftpService.getValidatedFiles()
                Then("Skal to FtpFil objekt returneres med alle kravlinjene") {
                    validFiles shouldHaveSize 2
                    validFiles.forOne { (name, kravlinjene) ->
                        name shouldBe FILE_OK
                        kravlinjene shouldHaveSize 101
                    }

                    validFiles.forOne { (name, kravlinjene) ->
                        name shouldBe validFile2
                        kravlinjene shouldHaveSize 10
                    }
                }

                And("Begge filene skal forbli i \"inbound\"") {
                    ftpService.listFiles(Directories.INBOUND).shouldContainExactlyInAnyOrder(FILE_OK, validFile2)
                    ftpService.listFiles(Directories.FAILED).shouldBeEmpty()
                }
            }
        }

        Given("Det finnes én valid og én invalid filer i \"inbound\" på FTP-serveren") {
            val fileWithError = "FeilAntallKrav.txt"
            SftpListener.putFiles(listOf(FILE_OK, filenameWithPath(fileWithError)), Directories.INBOUND)

            When("Filene valideres") {
                val validatedFiles = ftpService.getValidatedFiles()
                Then("Skal bare kravlinjene fra OK-filen returneres") {
                    validatedFiles shouldHaveSize 1
                    validatedFiles.single().should { file ->
                        file.name shouldBe FILE_OK
                        file.kravLinjer shouldHaveSize 101
                    }
                }
                And("Feilen fra Ikke-OK-filen skal lagres i database") {
                    val filValideringsfeil =
                        dataSource.transaction { session ->
                            filvalideringsFeilRepository.getAllValideringsFeil(session)
                        }

                    filValideringsfeil shouldHaveSize 1
                    filValideringsfeil.single().should { feil ->
                        feil.filnavn shouldBe fileWithError
                        feil.feilmelding shouldContain ErrorKeys.FEIL_I_ANTALL.value
                    }
                }
                And("OK-filen skal forbli i \"inbound\"") {
                    ftpService.listFiles(Directories.INBOUND).shouldContainExactly(FILE_OK)
                }
                And("Ikke-OK-filen skal flyttes til \"inbound\\feilfiler\"") {
                    ftpService.listFiles(Directories.FAILED).shouldContainExactly(fileWithError)
                }
            }
        }

        Given("Det finnes to invalid filer i \"inbound\" på FTP-serveren") {
            val file1 = "FeilUtbetalDato.txt"
            val file2 = "FeilSum.txt"
            val files = listOf(filenameWithPath(file1), filenameWithPath(file2))
            SftpListener.putFiles(files, Directories.INBOUND)

            When("Filene valideres") {
                val validatedFiles = ftpService.getValidatedFiles()
                Then("Skal en tom liste returneres") {
                    validatedFiles.shouldBeEmpty()
                }
                And("Feilene fra begge filene skal lagres i database") {
                    val filValideringsfeil =
                        dataSource.transaction { session ->
                            filvalideringsFeilRepository.getAllValideringsFeil(session)
                        }
                    filValideringsfeil shouldHaveSize 2
                    filValideringsfeil.forOne { feil ->
                        feil.filnavn shouldBe file1
                        feil.feilmelding shouldContain ErrorKeys.FEIL_I_DATO.value
                        feil.feilmelding shouldNotContain ErrorKeys.FEIL_I_ANTALL.value
                        feil.feilmelding shouldNotContain ErrorKeys.FEIL_I_SUM.value
                    }

                    filValideringsfeil.forOne { feil ->
                        feil.filnavn shouldBe file2
                        feil.feilmelding shouldContain ErrorKeys.FEIL_I_SUM.value
                        feil.feilmelding shouldNotContain ErrorKeys.FEIL_I_DATO.value
                        feil.feilmelding shouldNotContain ErrorKeys.FEIL_I_ANTALL.value
                    }
                }
                And("Alert skal sendes til Slack") {
                    val sendAlertFilenamesSlot = mutableListOf<String>()
                    val sendAlertMessagesSlot = mutableListOf<List<ErrorDetails>>()

                    verify(exactly = 2) { slackService.addErrors(capture(sendAlertFilenamesSlot), any(), capture(sendAlertMessagesSlot)) }
                    sendAlertFilenamesSlot.shouldContainExactlyInAnyOrder(file1, file2)

                    sendAlertMessagesSlot shouldHaveSize 2
                    sendAlertMessagesSlot.forOne {
                        it shouldHaveSize 1
                        it.single().should { errorDetail ->
                            errorDetail.header shouldBe ErrorKeys.FEIL_I_DATO
                        }
                    }
                    sendAlertMessagesSlot.forOne {
                        it shouldHaveSize 1
                        it.single().should { errorDetail ->
                            errorDetail.header shouldBe ErrorKeys.FEIL_I_DATO.value
                        }
                    }

                    coVerify(exactly = 2) { slackService.sendErrors() }
                }
                And("Begge filene skal flyttes til \"inbound\\feilfiler\"") {
                    ftpService.listFiles(Directories.INBOUND).shouldBeEmpty()
                    ftpService.listFiles(Directories.FAILED).shouldContainExactlyInAnyOrder(file1, file2)
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
