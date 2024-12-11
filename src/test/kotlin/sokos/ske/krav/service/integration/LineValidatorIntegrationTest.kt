package sokos.ske.krav.service.integration

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import sokos.ske.krav.client.SlackClient
import sokos.ske.krav.domain.nav.KravLinje
import sokos.ske.krav.service.DatabaseService
import sokos.ske.krav.service.FtpFil
import sokos.ske.krav.util.MockHttpClient
import sokos.ske.krav.util.SftpListener
import sokos.ske.krav.util.TestContainer
import sokos.ske.krav.validation.LineValidator
import java.math.BigDecimal
import java.time.LocalDate

internal class LineValidatorIntegrationTest :
    BehaviorSpec({
        extensions(SftpListener)
        val testContainer = TestContainer()
        val dbService = DatabaseService(testContainer.dataSource)
        Given("En fil har feil i linjer") {
            val kravLinje =
                KravLinje(
                    1,
                    "saksnummer_nav",
                    BigDecimal.ONE,
                    LocalDate.now(),
                    "gjelderID",
                    "20231201",
                    "20231212",
                    "KS KS",
                    "refgammelsak",
                    "20230112",
                    "bosted",
                    "beh",
                    "TA",
                    "arsak",
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    LocalDate.now().minusDays(1),
                    "1234",
                )
            val fileName = this.testCase.name.testName
            val fil = FtpFil(fileName, emptyList(), kravLinjer = listOf(kravLinje))

            When("Fil valideres") {
                LineValidator(SlackClient(client = MockHttpClient().getSlackClient())).validateNewLines(fil, dbService)

                Then("Skal valideringsfeilene lagres i database") {
                    val rs =
                        testContainer.dataSource.connection
                            .prepareStatement("""select * from valideringsfeil""")
                            .executeQuery()
                    rs.next() shouldBe true

                    rs.getString("filnavn") shouldBe fileName
                    rs.getString("saksnummer_nav") shouldBe kravLinje.saksnummerNav

                    rs.next() shouldBe false
                }
            }
        }
    })
