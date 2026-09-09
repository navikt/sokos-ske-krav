package no.nav.sokos.ske.krav.service.newversion

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.inspectors.forExactly
import io.kotest.inspectors.forNone
import io.kotest.inspectors.forOne
import io.kotest.inspectors.forValuesExactly
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import io.mockk.spyk
import org.slf4j.LoggerFactory

import no.nav.sokos.ske.krav.client.SlackClient
import no.nav.sokos.ske.krav.config.SftpConfig
import no.nav.sokos.ske.krav.domain.Krav
import no.nav.sokos.ske.krav.dto.slack.ErrorDetails
import no.nav.sokos.ske.krav.dto.slack.ExtraTags
import no.nav.sokos.ske.krav.listener.DBListener
import no.nav.sokos.ske.krav.listener.DBListener.dataSource
import no.nav.sokos.ske.krav.listener.DBListener.filvalideringsFeilRepository
import no.nav.sokos.ske.krav.listener.DBListener.kravRepository
import no.nav.sokos.ske.krav.listener.SftpListener
import no.nav.sokos.ske.krav.service.Directories
import no.nav.sokos.ske.krav.service.FtpService
import no.nav.sokos.ske.krav.service.SlackService
import no.nav.sokos.ske.krav.util.getAllKrav
import no.nav.sokos.ske.krav.util.getAllValideringsFeil
import no.nav.sokos.ske.krav.util.isGivenTest
import no.nav.sokos.ske.krav.util.shouldBe
import no.nav.sokos.ske.krav.util.shouldContain
import no.nav.sokos.ske.krav.util.transaction
import no.nav.sokos.ske.krav.validation.ErrorCategory
import no.nav.sokos.ske.krav.validation.ErrorKeys
import no.nav.sokos.ske.krav.validation.ErrorMessages

internal class FileHandlerIntegrationTest :
    BehaviorSpec(
        {
            extensions(SftpListener, DBListener)
            val fileHandlerLogger = LoggerFactory.getLogger(FileHandler::class.java) as Logger
            val logAppender = ListAppender<ILoggingEvent>()

            val slackClient =
                mockk<SlackClient> {
                    coJustRun { sendMessage(any<ErrorCategory>(), any<String>(), any<ExtraTags>(), any<List<ErrorDetails>>()) }
                }
            val slackService = spyk(SlackService(slackClient), recordPrivateCalls = true)

            val ftpService =
                FtpService(
                    dataSource = dataSource,
                    sftpConfig = SftpConfig(SftpListener.sftpProperties),
                    filValideringsfeilRepository = filvalideringsFeilRepository,
                    slackService = slackService,
                )
            val fileHandler =
                FileHandler(
                    ftpService = ftpService,
                    dataSource = dataSource,
                    kravRepository = kravRepository,
                    filValideringsfeilRepository = filvalideringsFeilRepository,
                    slackService = slackService,
                )

            beforeSpec {
                logAppender.start()
                fileHandlerLogger.addAppender(logAppender)
            }

            afterContainer { (testCase, _) ->
                if (testCase.isGivenTest()) {
                    DBListener.clearDB()
                    SftpListener.clearAllDirectories()
                    clearMocks(slackClient, slackService, answers = false)
                    logAppender.list.clear()
                }
            }

            afterSpec {
                fileHandlerLogger.detachAppender(logAppender)
                logAppender.stop()
            }

            Given("Det finnes ingen fil i \"INBOUND\"") {
                fileHandler.processFiles()

                Then("slutter vi prosessen uten å gjøre ingenting") {
                    logAppender.list shouldHaveSize 0
                    coVerify(exactly = 0) { slackService.sendErrors() }
                }
            }

            Given("Det finnes én fil i \"INBOUND\" som kan ikke parses") {
                val filename = "FeilAntallKrav.txt"
                SftpListener.putFile("validering/filvalidering/$filename")

                fileHandler.processFiles()

                Then("slutter vi prosessen uten å gjøre ingenting") {
                    logAppender.list.forNone { it.formattedMessage.contains("*** Starter lagring av") }
                }

                And("både \"INBOUND\" og \"OUTBOUND\" er tomme") {
                    ftpService.listFiles(Directories.INBOUND).shouldBeEmpty()
                    ftpService.listFiles(Directories.OUTBOUND).shouldBeEmpty()
                }
            }

            Given(("Det finnes én fil i \"INBOUND\" som har alle linjene ok")) {
                SftpListener.putFile("krav/TiNyeKrav.txt")

                fileHandler.processFiles()

                Then("Skal alle krav lagres i databasen") {
                    val allKrav =
                        dataSource.transaction { session ->
                            kravRepository.getAllKrav(session)
                        }
                    allKrav.shouldHaveSize(10)
                }

                And("Ingen feil skal lagres i databasen") {
                    val allFilvalideringsFeil =
                        dataSource.transaction { session ->
                            filvalideringsFeilRepository.getAllValideringsFeil(session)
                        }

                    allFilvalideringsFeil.shouldBeEmpty()
                }

                And("Filen flyttes til \"OUTBOUND\"") {
                    ftpService.listFiles(Directories.INBOUND).shouldBeEmpty()
                    ftpService.listFiles(Directories.OUTBOUND) shouldHaveSize 1
                }
            }

            Given("Det finnes én fil i \"INBOUND\" som har én linje med én feil") {
                val fileName = "EnLinjeFeilKravtype.txt"
                SftpListener.putFile("validering/linjevalidering/$fileName")

                fileHandler.processFiles()

                Then("Skal én feil og alle krav lagres i databasen") {
                    dataSource.transaction { session ->
                        kravRepository.getAllKrav(session).groupBy { it.linjenummer }.should { kravene ->
                            kravene shouldHaveSize 10
                            kravene.forValuesExactly(1) { it shouldHaveSize 2 }
                        }
                        filvalideringsFeilRepository.getAllValideringsFeil(session) shouldHaveSize 1
                    }
                }

                And("Filen flyttes til \"OUTBOUND\"") {
                    ftpService.listFiles(Directories.INBOUND).shouldBeEmpty()
                    ftpService.listFiles(Directories.OUTBOUND) shouldHaveSize 1
                }

                When("Feilmeldinger håndteres") {
                    Then("Skal én feilmelding dannes") {
                        val addErrorFilenameSlot = slot<String>()
                        val addErrorDetailsSlot = slot<List<ErrorDetails>>()

                        coVerify(exactly = 1) {
                            slackService.addErrors(capture(addErrorFilenameSlot), any<ErrorCategory>(), capture(addErrorDetailsSlot))
                        }
                        addErrorFilenameSlot.captured shouldBe fileName
                        addErrorDetailsSlot.captured.should { errorDetails ->
                            errorDetails shouldHaveSize 1
                            errorDetails.first().should {
                                it.header shouldBe ErrorKeys.KRAVTYPE_ERROR
                                it.description shouldContain ErrorMessages.KRAVTYPE_DOES_NOT_EXIST
                            }
                        }
                    }

                    And("Én alert med én feilmelding skal sendes") {
                        val sendAlertFilenameSlot = slot<String>()
                        val sendAlertErrorDetailsSlot = slot<List<ErrorDetails>>()

                        coVerify(exactly = 1) {
                            slackClient.sendMessage(any<ErrorCategory>(), capture(sendAlertFilenameSlot), any<ExtraTags>(), capture(sendAlertErrorDetailsSlot))
                        }
                        sendAlertFilenameSlot.captured shouldBe fileName
                        sendAlertErrorDetailsSlot.captured.should { errorDetails ->
                            errorDetails shouldHaveSize 1
                            errorDetails.first().should {
                                it.header shouldBe ErrorKeys.KRAVTYPE_ERROR
                                it.description shouldContain ErrorMessages.KRAVTYPE_DOES_NOT_EXIST
                                it.caseNumber.shouldNotBeNull()
                            }
                        }
                    }
                }
            }

            Given("Det finnes én fil i \"INBOUND\" som har én linje med tre forskjellige feil") {
                val fileName = "EnLinjeFlereFeil.txt"
                SftpListener.putFile("validering/linjevalidering/$fileName")

                fileHandler.processFiles()

                Then("Skal én feil og alle krav lagres i databasen") {
                    dataSource.transaction { session ->
                        kravRepository.getAllKrav(session).groupBy { it.linjenummer } shouldHaveSize 10
                        filvalideringsFeilRepository.getAllValideringsFeil(session) shouldHaveSize 1
                    }
                }

                And("Filen flyttes til \"OUTBOUND\"") {
                    ftpService.listFiles(Directories.INBOUND).shouldBeEmpty()
                    ftpService.listFiles(Directories.OUTBOUND) shouldHaveSize 1
                }

                When("Feilmeldinger håndteres") {
                    Then("Skal tre feilmeldinger dannes") {
                        val addErrorFilenameSlot = slot<String>()
                        val addErrorDetailsSlot = slot<List<ErrorDetails>>()

                        coVerify(exactly = 1) {
                            slackService.addErrors(capture(addErrorFilenameSlot), any<ErrorCategory>(), capture(addErrorDetailsSlot))
                        }
                        addErrorFilenameSlot.captured shouldBe fileName
                        addErrorDetailsSlot.captured.should { errorDetails ->
                            errorDetails shouldHaveSize 3
                            errorDetails.forExactly(1) { (header, description, caseNumber) ->
                                header shouldBe ErrorKeys.KRAVTYPE_ERROR
                                description shouldContain ErrorMessages.KRAVTYPE_DOES_NOT_EXIST
                                caseNumber.shouldNotBeNull()
                            }
                            errorDetails.forExactly(1) { (header, description, caseNumber) ->
                                header shouldBe ErrorKeys.VEDTAKSDATO_ERROR
                                description shouldContain ErrorMessages.VEDTAKSDATO_IS_IN_FUTURE
                                caseNumber.shouldNotBeNull()
                            }
                            errorDetails.forExactly(1) { (header, description, caseNumber) ->
                                header shouldBe ErrorKeys.SAKSNUMMER_ERROR
                                description shouldContain ErrorMessages.SAKSNUMMER_WRONG_FORMAT
                                caseNumber.shouldNotBeNull()
                            }
                        }
                    }

                    And("én alert med tre feilmeldinger skal sendes") {
                        val sendAlertFilenameSlot = slot<String>()
                        val sendAlertErrorMessageSlot = slot<List<ErrorDetails>>()

                        coVerify(exactly = 1) {
                            slackClient.sendMessage(any<ErrorCategory>(), capture(sendAlertFilenameSlot), any<ExtraTags>(), capture(sendAlertErrorMessageSlot))
                        }

                        sendAlertFilenameSlot.captured shouldBe fileName
                        sendAlertErrorMessageSlot.captured.should { errorDetails ->
                            errorDetails shouldHaveSize 3
                            errorDetails.forExactly(1) { (header, description, caseNumber) ->
                                header shouldBe ErrorKeys.KRAVTYPE_ERROR
                                description shouldContain ErrorMessages.KRAVTYPE_DOES_NOT_EXIST
                                caseNumber.shouldNotBeNull()
                            }

                            errorDetails.forExactly(1) { (header, description, caseNumber) ->
                                header shouldBe ErrorKeys.VEDTAKSDATO_ERROR
                                description shouldContain ErrorMessages.VEDTAKSDATO_IS_IN_FUTURE
                                caseNumber.shouldNotBeNull()
                            }

                            errorDetails.forExactly(1) { (header, description, caseNumber) ->
                                header shouldBe ErrorKeys.SAKSNUMMER_ERROR
                                description shouldContain ErrorMessages.SAKSNUMMER_WRONG_FORMAT
                                caseNumber.shouldNotBeNull()
                            }
                        }
                    }
                }
            }

            Given("Det finnes én fil i \"INBOUND\" som har seks linjer med samme type feil") {
                val fileName = "SeksLinjerSammeTypeFeil.txt"
                SftpListener.putFile("validering/linjevalidering/$fileName")

                fileHandler.processFiles()

                Then("Skal seks feil og alle krav lagres i databasen") {
                    dataSource.transaction { session ->
                        kravRepository.getAllKrav(session).groupBy { it.linjenummer } shouldHaveSize 10
                        filvalideringsFeilRepository.getAllValideringsFeil(session) shouldHaveSize 6
                    }
                }

                And("Filen flyttes til \"OUTBOUND\"") {
                    ftpService.listFiles(Directories.INBOUND).shouldBeEmpty()
                    ftpService.listFiles(Directories.OUTBOUND) shouldHaveSize 1
                }

                When("Feilmeldinger håndteres") {
                    Then("Skal seks feilmeldinger dannes") {
                        val addErrorFilenameSlot = slot<String>()
                        val addErrorDetailsSlot = slot<List<ErrorDetails>>()

                        coVerify(exactly = 1) {
                            slackService.addErrors(capture(addErrorFilenameSlot), any<ErrorCategory>(), capture(addErrorDetailsSlot))
                        }
                        addErrorFilenameSlot.captured shouldBe fileName
                        addErrorDetailsSlot.captured.should { errorDetails ->
                            errorDetails shouldHaveSize 6
                            errorDetails.forExactly(6) { (header, description, caseNumber) ->
                                header shouldBe ErrorKeys.KRAVTYPE_ERROR
                                description shouldContain ErrorMessages.KRAVTYPE_DOES_NOT_EXIST
                                caseNumber.shouldNotBeNull()
                            }
                        }
                    }

                    And("Én alert med én feilmelding skal sendes") {
                        val sendAlertFilenameSlot = slot<String>()
                        val sendAlertErrorDetailsSlot = slot<List<ErrorDetails>>()

                        coVerify(exactly = 1) {
                            slackClient.sendMessage(any<ErrorCategory>(), capture(sendAlertFilenameSlot), any<ExtraTags>(), capture(sendAlertErrorDetailsSlot))
                        }
                        sendAlertFilenameSlot.captured shouldBe fileName
                        sendAlertErrorDetailsSlot.captured.should { errorDetails ->
                            errorDetails shouldHaveSize 1
                            errorDetails.first().should {
                                it.header shouldBe ErrorKeys.KRAVTYPE_ERROR
                                it.description shouldContain ErrorMessages.KRAVTYPE_DOES_NOT_EXIST
                                it.caseNumber.shouldNotBeNull()
                            }
                        }
                    }
                }
            }

            Given("Det finnes én fil i \"INBOUND\" som har seks linjer med samme type feil og tre av disse linjene har ulike feil") {
                val fileName = "SeksLinjerSammeOgUlikeFeil.txt"
                SftpListener.putFile("validering/linjevalidering/$fileName")

                fileHandler.processFiles()

                Then("Skal seks feil og alle krav lagres i databasen") {
                    dataSource.transaction { session ->
                        kravRepository.getAllKrav(session).groupBy { it.linjenummer } shouldHaveSize 10
                        filvalideringsFeilRepository.getAllValideringsFeil(session) shouldHaveSize 6
                    }
                }

                And("Filen flyttes til \"OUTBOUND\"") {
                    ftpService.listFiles(Directories.INBOUND).shouldBeEmpty()
                    ftpService.listFiles(Directories.OUTBOUND) shouldHaveSize 1
                }

                When("Feilmeldinger håndteres") {
                    Then("Skal ni feilmeldinger dannes") {
                        val addErrorFilenameSlot = slot<String>()
                        val addErrorDetailsSlot = slot<List<ErrorDetails>>()

                        coVerify(exactly = 1) {
                            slackService.addErrors(capture(addErrorFilenameSlot), any<ErrorCategory>(), capture(addErrorDetailsSlot))
                        }
                        addErrorFilenameSlot.captured shouldBe fileName
                        addErrorDetailsSlot.captured.should { errorDetails ->
                            errorDetails shouldHaveSize 9
                            errorDetails.forExactly(6) { (header, description, caseNumber) ->
                                header shouldBe ErrorKeys.KRAVTYPE_ERROR
                                description shouldContain ErrorMessages.KRAVTYPE_DOES_NOT_EXIST
                                caseNumber.shouldNotBeNull()
                            }
                            errorDetails.forExactly(1) { (header, description, caseNumber) ->
                                header shouldBe ErrorKeys.VEDTAKSDATO_ERROR
                                description shouldContain ErrorMessages.VEDTAKSDATO_WRONG_FORMAT
                                caseNumber.shouldNotBeNull()
                            }
                            errorDetails.forExactly(1) { (header, description, caseNumber) ->
                                header shouldBe ErrorKeys.REFERANSENUMMERGAMMELSAK_ERROR
                                description shouldContain ErrorMessages.REFERANSENUMMERGAMMELSAK_WRONG_FORMAT
                                caseNumber.shouldNotBeNull()
                            }
                            errorDetails.forExactly(1) { (header, description, caseNumber) ->
                                header shouldBe ErrorKeys.SAKSNUMMER_ERROR
                                description shouldContain ErrorMessages.SAKSNUMMER_WRONG_FORMAT
                                caseNumber.shouldNotBeNull()
                            }
                        }
                    }
                }

                When("Én alert sendes") {
                    val sendAlertFilenameSlot = slot<String>()
                    val sendAlertErrorDetailsSlot = slot<List<ErrorDetails>>()

                    coVerify(exactly = 1) {
                        slackClient.sendMessage(any<ErrorCategory>(), capture(sendAlertFilenameSlot), any<ExtraTags>(), capture(sendAlertErrorDetailsSlot))
                    }
                    sendAlertFilenameSlot.captured shouldBe fileName
                    val sendAlertErrorDetails = sendAlertErrorDetailsSlot.captured
                    sendAlertErrorDetails shouldHaveSize 4

                    Then("Skal de seks like feilmeldingene aggregeres til én") {
                        sendAlertErrorDetails.forExactly(1) { (header, description, caseNumber) ->
                            header shouldBe ErrorKeys.KRAVTYPE_ERROR
                            description shouldContain ErrorMessages.KRAVTYPE_DOES_NOT_EXIST
                            caseNumber.shouldNotBeNull()
                        }
                    }

                    And("De tre ulike feilmeldingene skall ikke aggregeres") {
                        sendAlertErrorDetails.forExactly(1) { (header, description, caseNumber) ->
                            header shouldBe ErrorKeys.VEDTAKSDATO_ERROR
                            description shouldContain ErrorMessages.VEDTAKSDATO_WRONG_FORMAT
                            caseNumber.shouldNotBeNull()
                        }

                        sendAlertErrorDetails.forExactly(1) { (header, description, caseNumber) ->
                            header shouldBe ErrorKeys.REFERANSENUMMERGAMMELSAK_ERROR
                            description shouldContain ErrorMessages.REFERANSENUMMERGAMMELSAK_WRONG_FORMAT
                            caseNumber.shouldNotBeNull()
                        }
                        sendAlertErrorDetails.forExactly(1) { (header, description, caseNumber) ->
                            header shouldBe ErrorKeys.SAKSNUMMER_ERROR
                            description shouldContain ErrorMessages.SAKSNUMMER_WRONG_FORMAT
                            caseNumber.shouldNotBeNull()
                        }
                    }
                }
            }

            Given("Det finnes to fil i \"INBOUND\" og begge har alle linjene ok") {
                val file1 = "krav/TiNyeKrav.txt"
                val file2 = "AllValideringOk.txt"

                SftpListener.putFiles(listOf(file1, file2))
                fileHandler.processFiles()

                Then("Skal alle kravene fra begge filene lagres i databasen") {
                    val allKrav =
                        dataSource
                            .transaction { session ->
                                kravRepository.getAllKrav(session)
                            }.groupBy { it.filnavn }

                    allKrav shouldHaveSize 2
                    allKrav["TiNyeKrav.txt"]?.shouldHaveSize(10)
                    allKrav[file2]?.let { }
                }

                And("Ingen feil skal lagres i databasen") {
                    val allFilvalideringsFeil =
                        dataSource.transaction { session ->
                            filvalideringsFeilRepository.getAllValideringsFeil(session)
                        }

                    allFilvalideringsFeil.shouldBeEmpty()
                }

                And("Filen flyttes til \"OUTBOUND\"") {
                    ftpService.listFiles(Directories.INBOUND).shouldBeEmpty()
                    ftpService.listFiles(Directories.OUTBOUND) shouldHaveSize 2
                }
            }

            Given("Det finnes to fil i \"INBOUND\" og én av de har én linje som er feil") {
                val fileNameFeil = "EnLinjeFeilKravtype.txt"
                val fileNameOK = "TiNyeKrav.txt"

                SftpListener.putFiles(listOf("validering/linjevalidering/$fileNameFeil", "krav/$fileNameOK"))
                fileHandler.processFiles()

                Then("Skal kravene fra begge filene lagres i databasen") {
                    dataSource.transaction { session ->
                        val kravPerFil: Map<String, List<Krav>> = kravRepository.getAllKrav(session).groupBy { it.filnavn }
                        kravPerFil shouldHaveSize 2
                        kravPerFil.forOne { (fileName, kravene) ->
                            fileName shouldBe fileNameFeil
                            kravene shouldHaveSize 11
                        }
                        kravPerFil.forOne { (fileName, kravene) ->
                            fileName shouldBe fileNameOK
                            kravene shouldHaveSize 10
                        }
                    }
                }

                And("Bare én feil fra én fil skal lagres i databasen") {
                    dataSource.transaction { session ->
                        filvalideringsFeilRepository.getAllValideringsFeil(session).should { filValideringsfeil ->
                            filValideringsfeil shouldHaveSize 1
                            filValideringsfeil.single().filnavn shouldBe fileNameFeil
                        }
                    }
                }

                And("Begge filene skal flyttes til `OUTBOUND`") {
                    ftpService.listFiles(Directories.INBOUND).shouldBeEmpty()
                    ftpService.listFiles(Directories.OUTBOUND) shouldHaveSize 2
                }
            }

            Given("Det finnes to fil i \"INBOUND\" og begge har én filvalideringsfeil") {
                val path = "validering/linjevalidering/"
                val fileName1 = "EnLinjeFeilKravtype.txt"
                val fileName2 = "EnLinjeFlereFeil.txt"

                SftpListener.putFiles(listOf("$path$fileName1", "$path/$fileName2"))
                fileHandler.processFiles()

                Then("Skal alle kravene fra begge filene lagres i databasen") {
                    dataSource.transaction { session ->
                        val kravPerFil: Map<String, List<Krav>> = kravRepository.getAllKrav(session).groupBy { it.filnavn }
                        kravPerFil shouldHaveSize 2
                        kravPerFil.forOne { (fileName, kravene) ->
                            fileName shouldBe fileName1
                            kravene.groupBy { it.linjenummer } shouldHaveSize 10
                        }
                        kravPerFil.forOne { (fileName, kravene) ->
                            fileName shouldBe fileName2
                            kravene.groupBy { it.linjenummer } shouldHaveSize 10
                        }
                    }
                }

                And("Én feil per fil skal lagres i databasen") {
                    dataSource.transaction { session ->
                        filvalideringsFeilRepository.getAllValideringsFeil(session).should { filValideringsfeil ->
                            filValideringsfeil shouldHaveSize 2
                            filValideringsfeil.forOne { it.filnavn shouldBe fileName1 }
                            filValideringsfeil.forOne { it.filnavn shouldBe fileName2 }
                        }
                    }
                }

                And("Begge filene skal flyttes til `OUTBOUND`") {
                    ftpService.listFiles(Directories.INBOUND).shouldBeEmpty()
                    ftpService.listFiles(Directories.OUTBOUND) shouldHaveSize 2
                }
            }
        },
    )
