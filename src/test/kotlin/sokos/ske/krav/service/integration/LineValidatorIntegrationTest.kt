package sokos.ske.krav.service.integration

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import sokos.ske.krav.client.SlackClient
import sokos.ske.krav.domain.nav.KravLinje
import sokos.ske.krav.service.DatabaseService
import sokos.ske.krav.service.FtpFil
import sokos.ske.krav.util.MockHttpClient
import sokos.ske.krav.util.TestContainer
import sokos.ske.krav.validation.LineValidator
import java.math.BigDecimal
import java.time.LocalDate

internal class LineValidatorIntegrationTest :
    FunSpec({

        test("Når validering av linjer feiler skal valideringsfeilene lagres i database") {

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
            val testContainer = TestContainer()
            val fil = FtpFil(this.testCase.name.testName, emptyList(), kravLinjer = listOf(kravLinje))
            val dbService = DatabaseService(testContainer.dataSource)
            LineValidator(SlackClient(client = MockHttpClient().getSlackClient())).validateNewLines(fil, dbService)

            val rs =
                testContainer.dataSource.connection
                    .prepareStatement("""select * from valideringsfeil""")
                    .executeQuery()
            rs.next() shouldBe true

            rs.getString("filnavn") shouldBe this.testCase.name.testName
            rs.getString("saksnummer_nav") shouldBe kravLinje.saksnummerNav

            rs.next() shouldBe false
        }
    })
