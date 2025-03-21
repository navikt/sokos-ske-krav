package sokos.ske.krav.service.unit

import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import sokos.ske.krav.client.SkeClient
import sokos.ske.krav.client.SlackClient
import sokos.ske.krav.client.SlackService
import sokos.ske.krav.database.models.KravTable
import sokos.ske.krav.domain.ske.responses.MottaksStatusResponse
import sokos.ske.krav.service.DatabaseService
import sokos.ske.krav.service.StatusService
import sokos.ske.krav.util.MockHttpClient
import java.time.LocalDateTime

class StatusServiceTest :
    BehaviorSpec({

        fun setupServices(databaseService: DatabaseService): Pair<SlackService, StatusService> {
            val slackServiceSpy = spyk(SlackService(SlackClient(client = MockHttpClient().getSlackClient())), recordPrivateCalls = true)
            val statusServiceSpy = spyk(StatusService(mockk<SkeClient>(relaxed = true), databaseService, slackServiceSpy), recordPrivateCalls = true)
            coEvery { statusServiceSpy["processKravStatus"](any<KravTable>()) } returns
                mockk<MottaksStatusResponse>(relaxed = true) {
                    every { mottaksStatus } returns "RESKONTROFOERT"
                }

            return Pair(slackServiceSpy, statusServiceSpy)
        }

        Given("Det finnes krav som ikke er reskontroført etter 24t") {
            val databaseServiceMock =
                mockk<DatabaseService> {
                    every { getAllKravForStatusCheck() } returns
                        listOf(
                            mockk<KravTable>(relaxed = true) {
                                every { filnavn } returns "Testfil"
                                every { saksnummerNAV } returns "123"
                                every { status } returns "MOTTATT_UNDER_BEHANDLING"
                                every { tidspunktSendt } returns LocalDateTime.now().minusDays(2)
                            },
                            mockk<KravTable>(relaxed = true) {
                                every { filnavn } returns "Testfil"
                                every { saksnummerNAV } returns "456"
                                every { status } returns "KRAV_SENDT"
                                every { tidspunktSendt } returns LocalDateTime.now().minusHours(2)
                            },
                            mockk<KravTable>(relaxed = true) {
                                every { filnavn } returns "Testfil"
                                every { saksnummerNAV } returns "789"
                                every { status } returns "KRAV_SENDT"
                                every { tidspunktSendt } returns LocalDateTime.now().minusHours(24)
                            },
                            mockk<KravTable>(relaxed = true) {
                                every { filnavn } returns "Testfil"
                                every { saksnummerNAV } returns "101112"
                                every { status } returns "MOTTATT_UNDER_BEHANDLING"
                                every { tidspunktSendt } returns LocalDateTime.now().minusHours(25)
                            },
                        )
                }

            Then("Skal Slack alert sendes").config(enabled = false) {
                val (slackServiceSpy, statusService) = setupServices(databaseServiceMock)
                statusService.getMottaksStatus()
                coVerify(exactly = 3) {
                    slackServiceSpy.addError("Testfil", "Krav har blitt forsøkt resendt for lenge", any<Pair<String, String>>())
                }
            }
        }
    })
