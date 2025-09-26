package no.nav.sokos.ske.krav.service.unit

import java.time.LocalDateTime

import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk

import no.nav.sokos.ske.krav.client.SlackClient
import no.nav.sokos.ske.krav.client.SlackService
import no.nav.sokos.ske.krav.domain.Krav
import no.nav.sokos.ske.krav.service.DatabaseService
import no.nav.sokos.ske.krav.util.MockHttpClient
import no.nav.sokos.ske.krav.util.setupSkeServiceMock

class SkeServiceTest :
    BehaviorSpec({

        Given("Det finnes krav som ikke er reskontroført etter 24t") {
            val databaseServiceMock =
                mockk<DatabaseService> {
                    every { getAllKravForStatusCheck() } returns
                        listOf(
                            mockk<Krav>(relaxed = true) {
                                every { filnavn } returns "Testfil"
                                every { saksnummerNAV } returns "123"
                                every { status } returns "MOTTATT_UNDER_BEHANDLING"
                                every { tidspunktSendt } returns LocalDateTime.now().minusDays(2)
                            },
                            mockk<Krav>(relaxed = true) {
                                every { filnavn } returns "Testfil"
                                every { saksnummerNAV } returns "456"
                                every { status } returns "KRAV_SENDT"
                                every { tidspunktSendt } returns LocalDateTime.now().minusHours(2)
                            },
                            mockk<Krav>(relaxed = true) {
                                every { filnavn } returns "Testfil"
                                every { saksnummerNAV } returns "789"
                                every { status } returns "KRAV_SENDT"
                                every { tidspunktSendt } returns LocalDateTime.now().minusHours(24)
                            },
                            mockk<Krav>(relaxed = true) {
                                every { filnavn } returns "Testfil"
                                every { saksnummerNAV } returns "101112"
                                every { status } returns "MOTTATT_UNDER_BEHANDLING"
                                every { tidspunktSendt } returns LocalDateTime.now().minusHours(25)
                            },
                        )
                }

            Then("Skal Slack alert sendes") {
                val slackServiceSpy = spyk(SlackService(SlackClient(client = MockHttpClient().getSlackClient())), recordPrivateCalls = true)

                setupSkeServiceMock(databaseService = databaseServiceMock, slackService = slackServiceSpy).checkKravDateForAlert()
                coVerify(exactly = 3) {
                    slackServiceSpy.addError("Testfil", "Krav har blitt forsøkt resendt for lenge", any<Pair<String, String>>())
                }
            }
        }
    })
