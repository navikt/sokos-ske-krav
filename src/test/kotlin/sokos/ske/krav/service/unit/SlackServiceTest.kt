package sokos.ske.krav.service.unit

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import sokos.ske.krav.client.SlackClient
import sokos.ske.krav.client.SlackService

internal class SlackServiceTest :
    FunSpec({

        test("addError adds error messages to SlackService") {
            val headerSlots = mutableListOf<String>()
            val fileNameSlots = mutableListOf<String>()
            val errorSlots = mutableListOf<Map<String, List<String>>>()

            val slackClient =
                mockk<SlackClient>(relaxed = true) {
                    coEvery { sendMessage(any<String>(), any<String>(), any<Map<String, List<String>>>()) } answers {
                        headerSlots.add(firstArg())
                        fileNameSlots.add(secondArg())
                        errorSlots.add(thirdArg())
                    }
                }

            val slackService = SlackService(slackClient)

            slackService.addError(
                "file3.txt",
                "SyntaxError",
                mapOf(
                    "ParseProblem" to listOf("Unexpected token in line 42"),
                ),
            )

            slackService.sendErrors()

            headerSlots.size shouldBe 1
            headerSlots[0] shouldBe "SyntaxError"

            fileNameSlots.size shouldBe 1
            fileNameSlots[0] shouldBe "file3.txt"

            errorSlots.size shouldBe 1
            errorSlots[0].size shouldBe 1
            errorSlots[0]["ParseProblem"] shouldContainExactly listOf("Unexpected token in line 42")
        }

        test("consolidateErrors erstatter ikke meldinger når det er <=5 errors") {
            val headerSlots = mutableListOf<String>()
            val fileNameSlots = mutableListOf<String>()
            val errorSlots = mutableListOf<Map<String, List<String>>>()
            val slackClient =
                mockk<SlackClient>(relaxed = true) {
                    coEvery { sendMessage(any<String>(), any<String>(), any<Map<String, List<String>>>()) } answers {
                        headerSlots.add(firstArg())
                        fileNameSlots.add(secondArg())
                        errorSlots.add(thirdArg())
                    }
                }

            val slackService = SlackService(slackClient)

            slackService.addError(
                "file1.txt",
                "Validation",
                mapOf(
                    "DateError" to listOf("Invalid date format on line 1"),
                    "LengthError" to listOf("Field exceeds max length"),
                ),
            )

            slackService.addError(
                "file2.txt",
                "Processing",
                mapOf(
                    "ParseError" to listOf("Invalid syntax in line 10"),
                ),
            )

            slackService.sendErrors()

            headerSlots.size shouldBe 2
            headerSlots[0] shouldBe "Validation"
            headerSlots[1] shouldBe "Processing"

            fileNameSlots.size shouldBe 2
            fileNameSlots[0] shouldBe "file1.txt"
            fileNameSlots[1] shouldBe "file2.txt"

            errorSlots.size shouldBe 2
            errorSlots[0].size shouldBe 2
            errorSlots[0]["DateError"] shouldContainExactly listOf("Invalid date format on line 1")
            errorSlots[0]["LengthError"] shouldContainExactly listOf("Field exceeds max length")
            errorSlots[1].size shouldBe 1
            errorSlots[1]["ParseError"] shouldContainExactly listOf("Invalid syntax in line 10")
        }

        test("consolidateErrors erstatter meldinger når det er >5 errors") {
            val headerSlots = mutableListOf<String>()
            val fileNameSlots = mutableListOf<String>()
            val errorSlots = mutableListOf<Map<String, List<String>>>()
            val slackClient =
                mockk<SlackClient>(relaxed = true) {
                    coEvery { sendMessage(any<String>(), any<String>(), any<Map<String, List<String>>>()) } answers {
                        headerSlots.add(firstArg())
                        fileNameSlots.add(secondArg())
                        errorSlots.add(thirdArg())
                    }
                }

            val slackService = SlackService(slackClient)

            val lengthErrorList =
                listOf(
                    "Field exceeds max length",
                    "Field too short",
                )
            slackService.addError(
                "file1.txt",
                "Validation",
                mapOf(
                    "DateError" to
                        listOf(
                            "Invalid date format on line 1",
                            "Invalid date format on line 2",
                            "Invalid date format on line 3",
                            "Invalid date format on line 4",
                            "Invalid date format on line 5",
                            "Invalid date format on line 6",
                        ),
                    "LengthError" to lengthErrorList,
                ),
            )

            val parseErrorList =
                listOf(
                    "Invalid syntax in line 10",
                    "Unexpected token at line 20",
                    "Mismatched brackets on line 30",
                    "Unterminated string in line 40",
                    "Empty file in line 50",
                )
            slackService.addError(
                "file2.txt",
                "Processing",
                mapOf(
                    "ParseError" to parseErrorList,
                ),
            )

            slackService.sendErrors()
            headerSlots.size shouldBe 2
            headerSlots[0] shouldBe "Validation"
            headerSlots[1] shouldBe "Processing"

            fileNameSlots.size shouldBe 2
            fileNameSlots[0] shouldBe "file1.txt"
            fileNameSlots[1] shouldBe "file2.txt"

            errorSlots.size shouldBe 2
            errorSlots[0].size shouldBe 2
            errorSlots[0]["DateError"] shouldContainExactly listOf("6 av samme type feil: DateError. Sjekk avstemming")
            errorSlots[0]["LengthError"] shouldBe lengthErrorList
            errorSlots[1].size shouldBe 1
            errorSlots[1]["ParseError"] shouldBe parseErrorList
        }
    })
