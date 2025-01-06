package sokos.ske.krav.service.integration

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.spyk
import sokos.ske.krav.client.SlackClient
import sokos.ske.krav.database.models.ValideringsfeilTable
import sokos.ske.krav.database.toValideringsfeil
import sokos.ske.krav.domain.nav.KravLinje
import sokos.ske.krav.service.DatabaseService
import sokos.ske.krav.service.FtpFil
import sokos.ske.krav.util.MockHttpClient
import sokos.ske.krav.util.SftpListener
import sokos.ske.krav.util.TestContainer
import sokos.ske.krav.validation.LineValidator
import java.math.BigDecimal
import java.sql.Connection
import java.time.LocalDate

internal class LineValidatorIntegrationTest :
    BehaviorSpec({
        extensions(SftpListener)
        val testContainer = TestContainer()
        val dbService = DatabaseService(testContainer.dataSource)

        fun ftpFile(
            name: String,
            kravLinjer: List<KravLinje>,
        ) = FtpFil(name, emptyList(), kravLinjer)

        Given("1 linje har 1 feil") {
        }
        Given("1 linje har 3 forskjelloge feil") {}
        Given("6 linjer har samme type feil") {
            And("3 linjer har ulike feil") {}
        }

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
                val slackClient = spyk(SlackClient(client = MockHttpClient().getSlackClient()))
                LineValidator.validateNewLines(fil, dbService, slackClient)

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

        // TODO: Sjekke at aggregering blir riktig mtp database og slack
        Given("En fil har mange av samme type feil") {
        }
    })

private fun Connection.getValideringsFeil(
    filNavn: String,
    linjeNummer: Int,
): List<ValideringsfeilTable> =
    prepareStatement(
"""select * from valideringsfeil""".trimIndent(),
    ).executeQuery()
        .toValideringsfeil()

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
