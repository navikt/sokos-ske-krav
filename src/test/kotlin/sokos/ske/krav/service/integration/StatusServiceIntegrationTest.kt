package sokos.ske.krav.service.integration

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.spyk
import sokos.ske.krav.client.SkeClient
import sokos.ske.krav.client.SlackClient
import sokos.ske.krav.domain.Status
import sokos.ske.krav.security.MaskinportenAccessTokenClient
import sokos.ske.krav.service.DatabaseService
import sokos.ske.krav.service.StatusService
import sokos.ske.krav.util.MockHttpClient
import sokos.ske.krav.util.MockHttpClientUtils
import sokos.ske.krav.util.TestContainer
import sokos.ske.krav.util.getAllKrav

internal class StatusServiceIntegrationTest :
    BehaviorSpec({

        Given("Mottaksstatus er RESKONTROFOERT") {
            val testContainer = TestContainer()
            testContainer.migrate("KravSomSkalOppdateres.sql")

            val mottaksStatusResponse = MockHttpClientUtils.Responses.mottaksStatusResponse(status = Status.RESKONTROFOERT.value)
            val httpClient = mottaksStatusMockHttpClient(mottaksStatusResponse)

            val skeClient = SkeClient(skeEndpoint = "", client = httpClient, tokenProvider = mockk<MaskinportenAccessTokenClient>(relaxed = true))
            val dbService = DatabaseService(testContainer.dataSource)
            val slackClient = spyk(SlackClient(client = MockHttpClient().getSlackClient()))
            val statusService = StatusService(skeClient, dbService, slackClient)

            Then("Skal mottaksstatus settes til RESKONTROFOERT i database") {
                val allKravBeforeUpdate = testContainer.dataSource.connection.use { con -> con.getAllKrav() }
                allKravBeforeUpdate.filter { it.status == Status.RESKONTROFOERT.value }.size shouldBe 3

                statusService.getMottaksStatus()

                val allKravAfterUpdate = testContainer.dataSource.connection.use { con -> con.getAllKrav() }
                allKravAfterUpdate.filter { it.status == Status.RESKONTROFOERT.value }.size shouldBe 8
            }
            And("Alert skal ikke sendes") {
                coVerify(exactly = 0) {
                    slackClient.sendAsynkValideringsFeilFraSke(any<String>(), any<Pair<String, String>>())
                }
            }
        }
        Given("Mottaksstatus er VALIDERINGSFEIL") {
            val testContainer = TestContainer()
            testContainer.migrate("KravSomSkalOppdateres.sql")

            val status = "ORGANISASJONSNUMMER_FINNES_IKKE"
            val mottaksStatusResponse = MockHttpClientUtils.Responses.mottaksStatusResponse(status = Status.VALIDERINGSFEIL_MOTTAKSSTATUS.value)
            val valideringsFeilRespons = MockHttpClientUtils.Responses.valideringsfeilResponse(status, "Organisasjon med organisasjonsnummer=xxxxxxxxx finnes ikke")
            val httpClient = mottaksStatusMockHttpClient(mottaksStatusResponse, valideringsFeilRespons)

            val skeClient = SkeClient(skeEndpoint = "", client = httpClient, tokenProvider = mockk<MaskinportenAccessTokenClient>(relaxed = true))
            val dbService = DatabaseService(testContainer.dataSource)
            val slackClient = spyk(SlackClient(client = MockHttpClient().getSlackClient()))
            val statusService = StatusService(skeClient, dbService, slackClient)

            dbService.getAllFeilmeldinger().size shouldBe 0
            statusService.getMottaksStatus()

            Then("Skal feilmelding lagres i Feilmelding tabell") {
                val errorMessages = dbService.getAllFeilmeldinger()
                errorMessages.size shouldBe 5
                errorMessages.filter { it.error == status }.size shouldBe 5
            }
            And("Mottaksstatus skal settes til VALIDERINGSFEIL i database") {
                val allKravAfterUpdate = testContainer.dataSource.connection.use { con -> con.getAllKrav() }
                allKravAfterUpdate.filter { it.status == Status.VALIDERINGSFEIL_MOTTAKSSTATUS.value }.size shouldBe 5
            }
            And("Alert skal sendes til slack") {
                coVerify(exactly = 5) {
                    slackClient.sendAsynkValideringsFeilFraSke(any<String>(), any<Pair<String, String>>())
                }
            }
        }
    })

fun mottaksStatusMockHttpClient(
    mottaksStatusResponse: String,
    valideringsFeilResponse: String = MockHttpClientUtils.Responses.emptyValideringsfeilResponse(),
) = MockHttpClient().getClient(
    listOf(
        MockHttpClientUtils.MockRequestObj(mottaksStatusResponse, MockHttpClientUtils.EndepunktType.MOTTAKSSTATUS, HttpStatusCode.OK),
        MockHttpClientUtils.MockRequestObj(valideringsFeilResponse, MockHttpClientUtils.EndepunktType.HENT_VALIDERINGSFEIL, HttpStatusCode.OK),
    ),
)
