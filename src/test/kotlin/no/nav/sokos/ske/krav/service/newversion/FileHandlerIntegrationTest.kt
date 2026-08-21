package no.nav.sokos.ske.krav.service.newversion

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.inspectors.forExactly
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
import no.nav.sokos.ske.krav.dto.slack.ErrorDetails
import no.nav.sokos.ske.krav.dto.slack.ExtraTags
import no.nav.sokos.ske.krav.listener.DBListener
import no.nav.sokos.ske.krav.listener.DBListener.dataSource
import no.nav.sokos.ske.krav.listener.DBListener.filvalideringsFeilRepository
import no.nav.sokos.ske.krav.listener.DBListener.kravRepository
import no.nav.sokos.ske.krav.listener.SftpListener
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

            beforeContainer {
                if (it.isGivenTest()) {
                    DBListener.clearDB()
                    clearMocks(slackClient, slackService, answers = false)
                    logAppender.list.clear()
                }
            }

            afterContainer {
                fileHandlerLogger.detachAppender(logAppender)
                logAppender.stop()
            }

            Given("Det finnes ingen fil i INBOUND") {
                fileHandler.processFiles()

                Then("logger vi at serveren er tom") {
                    logAppender.list shouldHaveSize 1
                    logAppender.list.single().formattedMessage shouldBe "*** Ingen nye filer ***"
                }

                And("vi slutter prosessen") {
                    coVerify(exactly = 0) { slackService.sendErrors() }
                    dataSource.transaction { session ->
                        kravRepository.getAllKrav(session).shouldBeEmpty()
                        filvalideringsFeilRepository.getAllValideringsFeil(session).shouldBeEmpty()
                    }
                }
            }

            Given(("Alle linjene er ok")) {
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
            }

            Given("Én linje har en feil") {
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

            Given("Én linje har tre forskjellige feil") {
                val fileName = "EnLinjeFlereFeil.txt"
                SftpListener.putFile("validering/linjevalidering/$fileName")

                fileHandler.processFiles()

                Then("Skal én feil og alle krav lagres i databasen") {
                    dataSource.transaction { session ->
                        kravRepository.getAllKrav(session).groupBy { it.linjenummer } shouldHaveSize 10
                        filvalideringsFeilRepository.getAllValideringsFeil(session) shouldHaveSize 1
                    }
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

            Given("Seks linjer har samme type feil") {
                val fileName = "SeksLinjerSammeTypeFeil.txt"
                SftpListener.putFile("validering/linjevalidering/$fileName")

                fileHandler.processFiles()

                Then("Skal seks feil og alle krav lagres i databasen") {
                    dataSource.transaction { session ->
                        kravRepository.getAllKrav(session).groupBy { it.linjenummer } shouldHaveSize 10
                        filvalideringsFeilRepository.getAllValideringsFeil(session) shouldHaveSize 6
                    }
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

            Given("Seks linjer har samme type feil og tre av disse linjene har ulike feil") {
                val fileName = "SeksLinjerSammeOgUlikeFeil.txt"
                SftpListener.putFile("validering/linjevalidering/$fileName")

                fileHandler.processFiles()

                Then("Skal seks feil og alle krav lagres i databasen") {
                    dataSource.transaction { session ->
                        kravRepository.getAllKrav(session).groupBy { it.linjenummer } shouldHaveSize 10
                        filvalideringsFeilRepository.getAllValideringsFeil(session) shouldHaveSize 6
                    }
                }

                And("Feilmeldinger håndteres") {
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
            }
        },
    )
