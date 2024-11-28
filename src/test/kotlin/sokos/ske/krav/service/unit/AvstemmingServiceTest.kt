package sokos.ske.krav.service.unit

import io.kotest.core.spec.style.FunSpec
import io.mockk.every
import io.mockk.mockk
import sokos.ske.krav.database.models.KravTable
import sokos.ske.krav.service.DatabaseService
import java.time.LocalDateTime

class AvstemmingServiceTest :
    FunSpec({
        val kravData1 =
            mockk<KravTable> {
                every { saksnummerNAV } returns "saksnummer1"
                every { filnavn } returns "FilA"
                every { linjenummer } returns 10
                every { kravId } returns 1
                every { fagsystemId } returns "fagsystemId1"
                every { tidspunktOpprettet } returns LocalDateTime.now()
                every { kravkode } returns "kravkode1"
                every { kodeHjemmel } returns "kodeHjemmel1"
                every { status } returns "status1"
                every { tidspunktSisteStatus } returns LocalDateTime.now()
            }
        val kravData2 =
            mockk<KravTable> {
                every { saksnummerNAV } returns "saksnummer2"
                every { filnavn } returns "FilB"
                every { linjenummer } returns 20
                every { kravId } returns 2
                every { fagsystemId } returns "fagsystemId2"
                every { tidspunktOpprettet } returns LocalDateTime.now()
                every { kravkode } returns "kravkode2"
                every { kodeHjemmel } returns "kodeHjemmel2"
                every { status } returns "status2"
                every { tidspunktSisteStatus } returns LocalDateTime.now()
            }

        test("hentAvstemmingsRapportSomFil skal returnere avstemmingsrapporten på CSV format") {
            val kravliste = listOf(kravData1, kravData2)
            val dataSourceMock =
                mockk<DatabaseService> {
                    every { getAllKravForAvstemming() } returns kravliste
                }

            // TODO: Oppdater denne testen

 /*           AvstemmingService(dataSourceMock, mockk<FtpService>()).hentAvstemmingsRapportSomCSVFil().run {
                this shouldContain
                    "${kravData1.kravId},${kravData1.saksnummerNAV},${kravData1.tidspunktOpprettet},${kravData1.kravkode},${kravData1.kodeHjemmel},${kravData1.status},${kravData1.tidspunktSisteStatus}"
                this shouldContain
                    "${kravData2.kravId},${kravData2.saksnummerNAV},${kravData2.tidspunktOpprettet},${kravData2.kravkode},${kravData2.kodeHjemmel},${kravData2.status},${kravData2.tidspunktSisteStatus}"
            }*/
        }
    })
