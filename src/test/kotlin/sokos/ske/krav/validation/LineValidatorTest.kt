package sokos.ske.krav.validation

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import io.mockk.spyk
import sokos.ske.krav.client.SlackClient
import sokos.ske.krav.domain.Status
import sokos.ske.krav.domain.nav.KravLinje
import sokos.ske.krav.service.DatabaseService
import sokos.ske.krav.service.FtpFil
import sokos.ske.krav.util.MockHttpClient
import sokos.ske.krav.validation.LineValidationRules.ErrorKeys.KRAVTYPE_ERROR
import sokos.ske.krav.validation.LineValidationRules.ErrorKeys.PERIODE_ERROR
import sokos.ske.krav.validation.LineValidationRules.ErrorKeys.REFERANSENUMMERGAMMELSAK_ERROR
import sokos.ske.krav.validation.LineValidationRules.ErrorKeys.SAKSNUMMER_ERROR
import sokos.ske.krav.validation.LineValidationRules.ErrorKeys.UTBETALINGSDATO_ERROR
import sokos.ske.krav.validation.LineValidationRules.ErrorKeys.VEDTAKSDATO_ERROR
import sokos.ske.krav.validation.LineValidationRules.ErrorMessages.KRAVTYPE_DOES_NOT_EXIST
import sokos.ske.krav.validation.LineValidationRules.ErrorMessages.REFERANSENUMMERGAMMELSAK_WRONG_FORMAT
import sokos.ske.krav.validation.LineValidationRules.ErrorMessages.SAKSNUMMER_WRONG_FORMAT
import sokos.ske.krav.validation.LineValidationRules.ErrorMessages.VEDTAKSDATO_IS_IN_FUTURE
import sokos.ske.krav.validation.LineValidationRules.ErrorMessages.VEDTAKSDATO_WRONG_FORMAT
import sokos.ske.krav.validation.LineValidationRules.errorDate
import java.math.BigDecimal
import java.time.LocalDate

internal class LineValidatorTest :
    BehaviorSpec({
        val dsMock = mockk<DatabaseService>(relaxed = true)

        fun ftpFile(
            name: String,
            kravLinjer: List<KravLinje>,
        ) = FtpFil(name, emptyList(), kravLinjer)

        Given("Alle linjer er ok") {
            val kravLinjer = getKravlinjer()
            val fileName = this.testCase.name.testName

            When("Linjer valideres") {
                val lineValidatorSpy = spyk<LineValidator>(recordPrivateCalls = true)
                val validatedLines = lineValidatorSpy.validateNewLines(ftpFile(fileName, kravLinjer), dsMock, mockk<SlackClient>(relaxed = true))

                Then("Skal validering returnere ${kravLinjer.size} ok kravlinjer") {
                    val updatedLines = kravLinjer.map { it.copy(status = Status.KRAV_IKKE_SENDT.value) }
                    val validated = validatedLines.filter { it.status == Status.KRAV_IKKE_SENDT.value }
                    updatedLines.zip(validated).toSet().size shouldBe kravLinjer.size
                    updatedLines.sortedBy { it.saksnummerNav } shouldBe validated.sortedBy { it.saksnummerNav }
                }

                And("Ingen feil linjer") {
                    validatedLines.filter { it.status == Status.VALIDERINGSFEIL_AV_LINJE_I_FIL.value }.size shouldBe 0
                }
                And("Alert skal ikke sendes") {
                    coVerify(exactly = 0) {
                        lineValidatorSpy["sendAlert"](any<String>(), any<Map<String, List<String>>>(), any<SlackClient>())
                    }
                }
            }
        }

        Given("1 linje har har 1 feil") {
            val okKrav = getKravlinjer()
            val ikkeOkKrav = listOf(okKrav[0].copy(linjenummer = 6, kravKode = "MJ AU"))

            val kravLinjer = okKrav + ikkeOkKrav
            val fileName = this.testCase.name.testName
            val slackClientSpy = spyk(SlackClient(client = MockHttpClient().getSlackClient()))
            val lineValidatorSpy = spyk<LineValidator>(recordPrivateCalls = true)

            When("Linjer valideres") {
                val validatedLines = lineValidatorSpy.validateNewLines(ftpFile(fileName, kravLinjer), dsMock, slackClientSpy)

                Then("Skal validering returnere ${okKrav.size} ok kravlinjer") {
                    val updatedLines = okKrav.map { it.copy(status = Status.KRAV_IKKE_SENDT.value) }
                    val validated = validatedLines.filter { it.status == Status.KRAV_IKKE_SENDT.value }
                    (updatedLines + validated).toSet().size shouldBe okKrav.size
                    updatedLines.sortedBy { it.saksnummerNav } shouldBe validated.sortedBy { it.saksnummerNav }
                }

                And("Validering skal returnere ${ikkeOkKrav.size} feil-linjer") {
                    with(validatedLines.filter { it.status == Status.VALIDERINGSFEIL_AV_LINJE_I_FIL.value }) {
                        size shouldBe ikkeOkKrav.size
                        first() shouldBe ikkeOkKrav.first().copy(status = Status.VALIDERINGSFEIL_AV_LINJE_I_FIL.value)
                    }
                }

                And(" 1 Feilmelding skal dannes for alert") {
                    val sendAlertFilenameSlot = slot<String>()
                    val sendAlertMessagesSlot = slot<Map<String, List<String>>>()

                    coVerify(exactly = 1) {
                        lineValidatorSpy["sendAlert"](capture(sendAlertFilenameSlot), capture(sendAlertMessagesSlot), any<SlackClient>())
                    }

                    sendAlertFilenameSlot.captured shouldBe fileName
                    val capturedSendAlertMessages: Map<String, List<String>> = sendAlertMessagesSlot.captured
                    capturedSendAlertMessages.size shouldBe 1

                    capturedSendAlertMessages[VEDTAKSDATO_ERROR] shouldBe null
                    capturedSendAlertMessages[UTBETALINGSDATO_ERROR] shouldBe null
                    capturedSendAlertMessages[PERIODE_ERROR] shouldBe null
                    capturedSendAlertMessages[SAKSNUMMER_ERROR] shouldBe null
                    capturedSendAlertMessages[REFERANSENUMMERGAMMELSAK_ERROR] shouldBe null
                    capturedSendAlertMessages[KRAVTYPE_ERROR] shouldNotBe null
                    with(capturedSendAlertMessages[KRAVTYPE_ERROR]!!) {
                        size shouldBe 1
                        first() shouldContain KRAVTYPE_DOES_NOT_EXIST
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
            val okKrav = getKravlinjer()
            val ikkeOkKrav =
                listOf(
                    okKrav[0].copy(linjenummer = 6, kravKode = "MJ AU", vedtaksDato = LocalDate.now().plusMonths(1), saksnummerNav = "saksnummer_ø"),
                )

            val kravLinjer = okKrav + ikkeOkKrav
            val fileName = this.testCase.name.testName
            val slackClientSpy = spyk(SlackClient(client = MockHttpClient().getSlackClient()))
            val lineValidatorSpy = spyk<LineValidator>(recordPrivateCalls = true)

            When("Linjer valideres") {
                val validatedLines = lineValidatorSpy.validateNewLines(ftpFile(fileName, kravLinjer), dsMock, slackClientSpy)
                Then("Skal validering returnere ${okKrav.size} ok kravlinjer") {
                    val updatedLines = okKrav.map { it.copy(status = Status.KRAV_IKKE_SENDT.value) }
                    val validated = validatedLines.filter { it.status == Status.KRAV_IKKE_SENDT.value }
                    (updatedLines + validated).toSet().size shouldBe okKrav.size
                    updatedLines.sortedBy { it.saksnummerNav } shouldBe validated.sortedBy { it.saksnummerNav }
                }
                And("Validering skal returnere ${ikkeOkKrav.size} feil-linjer") {
                    with(validatedLines.filter { it.status == Status.VALIDERINGSFEIL_AV_LINJE_I_FIL.value }) {
                        size shouldBe ikkeOkKrav.size
                        first() shouldBe ikkeOkKrav.first().copy(status = Status.VALIDERINGSFEIL_AV_LINJE_I_FIL.value)
                    }
                }
                And("3 feilmeldinger skal dannes for alert") {
                    val sendAlertFilenameSlot = slot<String>()
                    val sendAlertMessagesSlot = slot<Map<String, List<String>>>()

                    coVerify(exactly = 1) {
                        lineValidatorSpy["sendAlert"](capture(sendAlertFilenameSlot), capture(sendAlertMessagesSlot), any<SlackClient>())
                    }

                    sendAlertFilenameSlot.captured shouldBe fileName
                    val capturedSendAlertMessages: Map<String, List<String>> = sendAlertMessagesSlot.captured
                    capturedSendAlertMessages.size shouldBe 3

                    capturedSendAlertMessages[UTBETALINGSDATO_ERROR] shouldBe null
                    capturedSendAlertMessages[PERIODE_ERROR] shouldBe null
                    capturedSendAlertMessages[REFERANSENUMMERGAMMELSAK_ERROR] shouldBe null

                    capturedSendAlertMessages[VEDTAKSDATO_ERROR] shouldNotBe null
                    capturedSendAlertMessages[SAKSNUMMER_ERROR] shouldNotBe null
                    capturedSendAlertMessages[KRAVTYPE_ERROR] shouldNotBe null

                    with(capturedSendAlertMessages[VEDTAKSDATO_ERROR]!!) {
                        size shouldBe 1
                        first() shouldContain VEDTAKSDATO_IS_IN_FUTURE
                    }
                    with(capturedSendAlertMessages[SAKSNUMMER_ERROR]!!) {
                        size shouldBe 1
                        first() shouldContain SAKSNUMMER_WRONG_FORMAT
                    }

                    with(capturedSendAlertMessages[KRAVTYPE_ERROR]!!) {
                        size shouldBe 1
                        first() shouldContain KRAVTYPE_DOES_NOT_EXIST
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
            val okKrav = getKravlinjer()
            val ikkeOkKrav =
                listOf(
                    okKrav[0].copy(linjenummer = 6, kravKode = "MJ AU"),
                    okKrav[0].copy(linjenummer = 7, kravKode = "MJ AU"),
                    okKrav[0].copy(linjenummer = 8, kravKode = "MJ AU"),
                    okKrav[0].copy(linjenummer = 9, kravKode = "MJ AU"),
                    okKrav[0].copy(linjenummer = 10, kravKode = "MJ AU"),
                    okKrav[0].copy(linjenummer = 11, kravKode = "MJ AU"),
                )

            When("Linjer valideres") {
                val kravLinjer = okKrav + ikkeOkKrav
                val fileName = this.testCase.name.testName
                val slackClientSpy = spyk(SlackClient(client = MockHttpClient().getSlackClient()))
                val lineValidatorSpy = spyk<LineValidator>(recordPrivateCalls = true)
                val validatedLines = lineValidatorSpy.validateNewLines(ftpFile(fileName, kravLinjer), dsMock, slackClientSpy)

                Then("Skal validering returnere ${okKrav.size} ok kravlinjer") {
                    val updatedLines = okKrav.map { it.copy(status = Status.KRAV_IKKE_SENDT.value) }
                    val validated = validatedLines.filter { it.status == Status.KRAV_IKKE_SENDT.value }
                    (updatedLines + validated).toSet().size shouldBe okKrav.size
                    updatedLines.sortedBy { it.saksnummerNav } shouldBe validated.sortedBy { it.saksnummerNav }
                }
                And("Validering skal returnere ${ikkeOkKrav.size} feil-linjer") {
                    with(validatedLines.filter { it.status == Status.VALIDERINGSFEIL_AV_LINJE_I_FIL.value }) {
                        size shouldBe ikkeOkKrav.size
                        first() shouldBe ikkeOkKrav.first().copy(status = Status.VALIDERINGSFEIL_AV_LINJE_I_FIL.value)
                    }
                }
                And("6 feilmeldinger av samme type skal dannes for alert") {
                    val sendAlertFilenameSlot = slot<String>()
                    val sendAlertMessagesSlot = slot<Map<String, List<String>>>()

                    coVerify(exactly = 1) {
                        lineValidatorSpy["sendAlert"](capture(sendAlertFilenameSlot), capture(sendAlertMessagesSlot), any<SlackClient>())
                    }

                    sendAlertFilenameSlot.captured shouldBe fileName
                    val capturedSendAlertMessages: Map<String, List<String>> = sendAlertMessagesSlot.captured
                    capturedSendAlertMessages.size shouldBe 1

                    capturedSendAlertMessages[VEDTAKSDATO_ERROR] shouldBe null
                    capturedSendAlertMessages[UTBETALINGSDATO_ERROR] shouldBe null
                    capturedSendAlertMessages[PERIODE_ERROR] shouldBe null
                    capturedSendAlertMessages[SAKSNUMMER_ERROR] shouldBe null
                    capturedSendAlertMessages[REFERANSENUMMERGAMMELSAK_ERROR] shouldBe null
                    capturedSendAlertMessages[KRAVTYPE_ERROR] shouldNotBe null
                    with(capturedSendAlertMessages[KRAVTYPE_ERROR]!!) {
                        size shouldBe 6
                        filter { it.contains(KRAVTYPE_DOES_NOT_EXIST) }.size shouldBe 6
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
                            capturedErrorMessages.keys.first() shouldContain KRAVTYPE_DOES_NOT_EXIST
                        }
                    }
                }
            }

            And("3 linjer har ulike feil") {
                val ikkeOkKravMedUlikeFeil =
                    listOf(
                        ikkeOkKrav[0],
                        ikkeOkKrav[1],
                        ikkeOkKrav[2],
                        ikkeOkKrav[3].copy(saksnummerNav = "saksnummer_ø"),
                        ikkeOkKrav[4].copy(referansenummerGammelSak = "refgammel_ø"),
                        ikkeOkKrav[5].copy(vedtaksDato = errorDate),
                    )

                When("Linjer valideres") {
                    val kravLinjer = okKrav + ikkeOkKravMedUlikeFeil
                    val fileName = this.testCase.name.testName
                    val slackClientSpy = spyk(SlackClient(client = MockHttpClient().getSlackClient()))
                    val lineValidatorSpy = spyk<LineValidator>(recordPrivateCalls = true)
                    val validatedLines = lineValidatorSpy.validateNewLines(ftpFile(fileName, kravLinjer), dsMock, slackClientSpy)

                    Then("Skal validering returnere ${okKrav.size} ok kravlinjer") {
                        val updatedLines = okKrav.map { it.copy(status = Status.KRAV_IKKE_SENDT.value) }
                        val validated = validatedLines.filter { it.status == Status.KRAV_IKKE_SENDT.value }
                        (updatedLines + validated).toSet().size shouldBe okKrav.size
                        updatedLines.sortedBy { it.saksnummerNav } shouldBe validated.sortedBy { it.saksnummerNav }
                    }
                    And("Validering skal returnere ${ikkeOkKravMedUlikeFeil.size} feil-linjer") {
                        with(validatedLines.filter { it.status == Status.VALIDERINGSFEIL_AV_LINJE_I_FIL.value }) {
                            size shouldBe ikkeOkKrav.size
                            first() shouldBe ikkeOkKrav.first().copy(status = Status.VALIDERINGSFEIL_AV_LINJE_I_FIL.value)
                        }
                    }
                    And("9 feilmeldinger skal dannes for alert") {
                        val sendAlertFilenameSlot = slot<String>()
                        val sendAlertMessagesSlot = slot<Map<String, List<String>>>()

                        coVerify(exactly = 1) {
                            lineValidatorSpy["sendAlert"](capture(sendAlertFilenameSlot), capture(sendAlertMessagesSlot), any<SlackClient>())
                        }

                        sendAlertFilenameSlot.captured shouldBe fileName
                        val capturedSendAlertMessages: Map<String, List<String>> = sendAlertMessagesSlot.captured
                        capturedSendAlertMessages.size shouldBe 4

                        capturedSendAlertMessages[UTBETALINGSDATO_ERROR] shouldBe null
                        capturedSendAlertMessages[PERIODE_ERROR] shouldBe null
                        capturedSendAlertMessages[VEDTAKSDATO_ERROR] shouldNotBe null
                        capturedSendAlertMessages[SAKSNUMMER_ERROR] shouldNotBe null
                        capturedSendAlertMessages[REFERANSENUMMERGAMMELSAK_ERROR] shouldNotBe null
                        capturedSendAlertMessages[KRAVTYPE_ERROR] shouldNotBe null
                        with(capturedSendAlertMessages[KRAVTYPE_ERROR]!!) {
                            size shouldBe 6
                            filter { it.contains(KRAVTYPE_DOES_NOT_EXIST) }.size shouldBe 6
                        }

                        with(capturedSendAlertMessages[VEDTAKSDATO_ERROR]!!) {
                            size shouldBe 1
                            filter { it.contains(VEDTAKSDATO_WRONG_FORMAT) }.size shouldBe 1
                        }

                        with(capturedSendAlertMessages[REFERANSENUMMERGAMMELSAK_ERROR]!!) {
                            size shouldBe 1
                            filter { it.contains(REFERANSENUMMERGAMMELSAK_WRONG_FORMAT) }.size shouldBe 1
                        }
                        with(capturedSendAlertMessages[SAKSNUMMER_ERROR]!!) {
                            size shouldBe 1
                            filter { it.contains(SAKSNUMMER_WRONG_FORMAT) }.size shouldBe 1
                        }

                        When("Alert Lages") {
                            val sendLinjevalideringsMeldingFilenameSlot = slot<String>()
                            val sendLinjevalideringsMeldingMessagesSlot = slot<Map<String, List<String>>>()

                            coVerify(exactly = 1) {
                                slackClientSpy["sendLinjevalideringsMelding"](capture(sendLinjevalideringsMeldingFilenameSlot), capture(sendLinjevalideringsMeldingMessagesSlot))
                            }
                            sendLinjevalideringsMeldingFilenameSlot.captured shouldBe fileName
                            val capturedErrorMessages = sendLinjevalideringsMeldingMessagesSlot.captured
                            Then("Skal de 6 like feilmeldingene aggregeres") {
                                capturedErrorMessages.size shouldBe 4
                                capturedErrorMessages.keys.filter { it.contains(KRAVTYPE_DOES_NOT_EXIST) }.size shouldBe 1
                            }
                            Then("Skal de 3 ulike feilmeldingene ikke aggregeres") {
                                capturedErrorMessages.keys.filter { it.contains(VEDTAKSDATO_ERROR) }.size shouldBe 1
                                capturedErrorMessages.keys.filter { it.contains(REFERANSENUMMERGAMMELSAK_ERROR) }.size shouldBe 1
                                capturedErrorMessages.keys.filter { it.contains(SAKSNUMMER_ERROR) }.size shouldBe 1
                            }
                        }
                    }
                }
            }
        }
    })

private fun getKravlinjer(): MutableList<KravLinje> {
    val okLinje =
        KravLinje(
            linjenummer = 1,
            saksnummerNav = "saksnummer",
            belop = BigDecimal.ONE,
            vedtaksDato = LocalDate.now(),
            gjelderId = "gjelderID",
            periodeFOM = "20231201",
            periodeTOM = "20231212",
            kravKode = "KS KS",
            referansenummerGammelSak = "refgammelsak",
            transaksjonsDato = "20230112",
            enhetBosted = "bosted",
            enhetBehandlende = "beh",
            kodeHjemmel = "T",
            kodeArsak = "arsak",
            belopRente = BigDecimal.ONE,
            fremtidigYtelse = BigDecimal.ONE,
            utbetalDato = LocalDate.now().minusDays(1),
            fagsystemId = "1234",
        )
    return mutableListOf(okLinje, okLinje.copy(linjenummer = 2, saksnummerNav = "saksnummer2"), okLinje.copy(linjenummer = 3, saksnummerNav = "saksnummer3"), okLinje.copy(linjenummer = 4, saksnummerNav = "saksnummer4"), okLinje.copy(linjenummer = 5, saksnummerNav = "saksnummer5"))
}
