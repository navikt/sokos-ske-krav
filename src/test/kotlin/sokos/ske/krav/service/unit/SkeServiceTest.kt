package sokos.ske.krav.service.unit

import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import sokos.ske.krav.client.SlackClient
import sokos.ske.krav.client.SlackService
import sokos.ske.krav.database.models.KravTable
import sokos.ske.krav.service.DatabaseService
import sokos.ske.krav.service.SkeService
import sokos.ske.krav.util.MockHttpClient
import java.time.LocalDateTime

class SkeServiceTest :
    BehaviorSpec({

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

            Then("Skal Slack alert sendes") {
                val slackServiceSpy = spyk(SlackService(SlackClient(client = MockHttpClient().getSlackClient())), recordPrivateCalls = true)
                val skeServiceSpy = spyk(SkeService(databaseService = databaseServiceMock, slackService = slackServiceSpy))

                skeServiceSpy.checkKravDateForAlert()
                coVerify(exactly = 3) {
                    slackServiceSpy.addError("Testfil", "Krav har blitt forsøkt resendt for lenge", any<Pair<String, String>>())
                }
            }
        }
    })
