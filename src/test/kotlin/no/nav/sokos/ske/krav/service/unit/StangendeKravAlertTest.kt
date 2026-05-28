package no.nav.sokos.ske.krav.service.unit

import java.time.LocalDateTime

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.string.shouldContain
import io.ktor.server.config.ApplicationConfig
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.slf4j.LoggerFactory

import no.nav.sokos.ske.krav.config.PropertiesConfig
import no.nav.sokos.ske.krav.domain.Krav
import no.nav.sokos.ske.krav.domain.Status
import no.nav.sokos.ske.krav.repository.KravRepository
import no.nav.sokos.ske.krav.service.SkeService
import no.nav.sokos.ske.krav.util.setupSkeServiceMock

class StangendeKravAlertTest :
    BehaviorSpec({
        beforeSpec {
            mockkObject(PropertiesConfig)
            every { PropertiesConfig.config } returns ApplicationConfig("application-test.conf")
        }
        val skeServiceLogger = LoggerFactory.getLogger(SkeService::class.java) as Logger
        val logAppender = ListAppender<ILoggingEvent>()

        beforeTest {
            logAppender.start()
            skeServiceLogger.addAppender(logAppender)
        }

        afterTest {
            skeServiceLogger.detachAppender(logAppender)
            logAppender.stop()
        }
        Given("Det finnes krav som ikke er reskontroført etter 24t") {
            val kravRepositoryMock =
                mockk<KravRepository> {
                    every { getAllKravForStatusCheck() } returns mockedKrav()
                }

            When("checkForStangendeKrav kjøres") {
                setupSkeServiceMock(kravRepository = kravRepositoryMock).checkForStangendeKrav()
                val message = logAppender.list.map { it.formattedMessage }.single()

                Then("skal loggmeldingen inneholde totalt antall krav") {
                    message shouldContain "4 krav er blitt forsøkt resendt i over 24 timer"
                }

                And("skal loggmeldingen inneholde krav fra INFOTRYGD sendt for 1 dag siden") {
                    message shouldContain "2 krav fra INFOTRYGD har blitt forsøkt resendt i 1 dag(er)"
                }

                And("skal loggmeldingen inneholde krav fra INFOTRYGD sendt for 2 dager siden") {
                    message shouldContain "1 krav fra INFOTRYGD har blitt forsøkt resendt i 2 dag(er)"
                }

                And("skal loggmeldingen inneholde krav fra OB04 sendt for 2 dager siden") {
                    message shouldContain "1 krav fra OB04 har blitt forsøkt resendt i 2 dag(er)"
                }
            }
        }

        afterSpec {
            unmockkObject(PropertiesConfig)
        }
    })

private fun mockedKrav() =
    listOf(
        mockk<Krav>(relaxed = true) {
            every { filnavn } returns "Testfil-OS"
            every { avsender } returns "OB04"
            every { saksnummerNAV } returns "123"
            every { status } returns Status.MOTTATT_UNDER_BEHANDLING
            every { tidspunktSendt } returns LocalDateTime.now().minusDays(2)
        },
        mockk<Krav>(relaxed = true) {
            every { filnavn } returns "Testfil-OS"
            every { avsender } returns "OB04"
            every { status } returns Status.KRAV_SENDT
            every { tidspunktSendt } returns LocalDateTime.now().minusHours(2)
        },
        mockk<Krav>(relaxed = true) {
            every { filnavn } returns "Testfil-Infotrygd"
            every { avsender } returns "INFOTRYGD"
            every { saksnummerNAV } returns "789"
            every { status } returns Status.KRAV_SENDT
            every { tidspunktSendt } returns LocalDateTime.now().minusHours(24)
        },
        mockk<Krav>(relaxed = true) {
            every { filnavn } returns "Testfil-Infotrygd"
            every { avsender } returns "INFOTRYGD"
            every { status } returns Status.MOTTATT_UNDER_BEHANDLING
            every { tidspunktSendt } returns LocalDateTime.now().minusHours(25)
        },
        mockk<Krav>(relaxed = true) {
            every { filnavn } returns "Testfil-Infotrygd"
            every { avsender } returns "INFOTRYGD"
            every { status } returns Status.MOTTATT_UNDER_BEHANDLING
            every { tidspunktSendt } returns LocalDateTime.now().minusHours(49)
        },
    )
