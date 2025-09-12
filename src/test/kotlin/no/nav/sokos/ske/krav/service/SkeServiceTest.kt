package no.nav.sokos.ske.krav.service

import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.mockk

import no.nav.sokos.ske.krav.client.SlackService
import no.nav.sokos.ske.krav.config.SftpConfig
import no.nav.sokos.ske.krav.listener.PostgresListener
import no.nav.sokos.ske.krav.listener.SftpListener
import no.nav.sokos.ske.krav.listener.WiremockListener

class SkeServiceTest :
    BehaviorSpec({
        extensions(PostgresListener, WiremockListener, SftpListener)

        val slackService = mockk<SlackService>(relaxed = true)
        val ftpService: FtpService by lazy {
            FtpService(SftpConfig(SftpListener.sftpProperties))
        }

        val skeService: SkeService by lazy {
            SkeService(
                dataSource = PostgresListener.dataSource,
                slackService = slackService,
                ftpService = ftpService,
            )
        }

        Given("Det finnes krav som ikke er reskontroført etter 24t") {
//            val databaseServiceMock =
//                mockk<DatabaseService> {
//                    every { getAllKravForStatusCheck() } returns
//                        listOf(
//                            mockk<KravTable>(relaxed = true) {
//                                every { filnavn } returns "Testfil"
//                                every { saksnummerNAV } returns "123"
//                                every { status } returns "MOTTATT_UNDER_BEHANDLING"
//                                every { tidspunktSendt } returns LocalDateTime.now().minusDays(2)
//                            },
//                            mockk<KravTable>(relaxed = true) {
//                                every { filnavn } returns "Testfil"
//                                every { saksnummerNAV } returns "456"
//                                every { status } returns "KRAV_SENDT"
//                                every { tidspunktSendt } returns LocalDateTime.now().minusHours(2)
//                            },
//                            mockk<KravTable>(relaxed = true) {
//                                every { filnavn } returns "Testfil"
//                                every { saksnummerNAV } returns "789"
//                                every { status } returns "KRAV_SENDT"
//                                every { tidspunktSendt } returns LocalDateTime.now().minusHours(24)
//                            },
//                            mockk<KravTable>(relaxed = true) {
//                                every { filnavn } returns "Testfil"
//                                every { saksnummerNAV } returns "101112"
//                                every { status } returns "MOTTATT_UNDER_BEHANDLING"
//                                every { tidspunktSendt } returns LocalDateTime.now().minusHours(25)
//                            },
//                        )
//                }

            Then("Skal Slack alert sendes") {
//                val slackServiceSpy = spyk(SlackService(SlackClient(client = MockHttpClient().getSlackClient())), recordPrivateCalls = true)
//
//                setupSkeServiceMock(databaseService = databaseServiceMock, slackService = slackServiceSpy).checkKravDateForAlert()
//                coVerify(exactly = 3) {
//                    slackServiceSpy.addError("Testfil", "Krav har blitt forsøkt resendt for lenge", any<Pair<String, String>>())
//                }
            }
        }
    })
