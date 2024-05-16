package sokos.ske.krav.service.unit

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.mockk.every
import io.mockk.mockk
import sokos.ske.krav.database.models.FeilmeldingTable
import sokos.ske.krav.database.models.KravTable
import sokos.ske.krav.service.AvstemmingService
import sokos.ske.krav.service.DatabaseService
import sokos.ske.krav.service.FtpService
import java.time.LocalDateTime

class AvstemmingServiceTest : FunSpec({
    val kravData1 = mockk<KravTable> {
        every { saksnummerNAV } returns "saksnummer1"
        every { kravId } returns 1
        every { fagsystemId } returns "fagsystemId1"
        every { tidspunktOpprettet } returns LocalDateTime.now()
        every { kravkode } returns "kravkode1"
        every { kodeHjemmel } returns "kodeHjemmel1"
        every { status } returns "status1"
        every { tidspunktSisteStatus } returns LocalDateTime.now()
    }
    val kravData2 = mockk<KravTable> {
        every { saksnummerNAV } returns "saksnummer2"
        every { kravId } returns 2
        every { fagsystemId } returns "fagsystemId2"
        every { tidspunktOpprettet } returns LocalDateTime.now()
        every { kravkode } returns "kravkode2"
        every { kodeHjemmel } returns "kodeHjemmel2"
        every { status } returns "status2"
        every { tidspunktSisteStatus } returns LocalDateTime.now()
    }

    test("hentAvstemmingsRapport skal returnere en html med krav som har feilet") {
        val dataSourceMock = mockk<DatabaseService> {
            every { getAllKravForAvstemming() } returns listOf(kravData1, kravData2)
            every { getErrorMessageForKravId(any<Long>()) } returns listOf(mockk<FeilmeldingTable> {
                every { melding } returns "feilmelding melding"
            })
        }

        AvstemmingService(dataSourceMock, mockk<FtpService>()).hentAvstemmingsRapport().run {
            this shouldContain "Åpne statuser"
            this shouldContain "Fjern fra liste"
            this shouldContain "Last ned .csv fil"
            this shouldContain "feilmelding melding"

            this shouldContain kravData1.kravId.toString()
            this shouldContain kravData1.saksnummerNAV
            this shouldContain kravData1.fagsystemId
            this shouldContain kravData1.tidspunktOpprettet.toString()
            this shouldContain kravData1.kravkode
            this shouldContain kravData1.kodeHjemmel
            this shouldContain kravData1.status
            this shouldContain kravData1.tidspunktSisteStatus.toString()

            this shouldContain kravData2.kravId.toString()
            this shouldContain kravData2.saksnummerNAV
            this shouldContain kravData2.fagsystemId
            this shouldContain kravData2.tidspunktOpprettet.toString()
            this shouldContain kravData2.kravkode
            this shouldContain kravData2.kodeHjemmel
            this shouldContain kravData2.status
            this shouldContain kravData2.tidspunktSisteStatus.toString()
        }

    }

    test("hentKravSomSkalresendes skal returnere en html med krav som skal resendes") {
        val dataSourceMock = mockk<DatabaseService> {
            every { getAllKravForResending() } returns listOf(kravData1, kravData2)
            every { getErrorMessageForKravId(any<Long>()) } returns listOf(mockk<FeilmeldingTable> {
                every { melding } returns "feilmelding melding"
            })
        }

        AvstemmingService(dataSourceMock, mockk<FtpService>()).hentKravSomSkalresendes().run {
            this shouldContain "Krav Som Skal Resendes"
            this shouldNotContain "Fjern fra liste"
            this shouldNotContain "Last ned .csv fil"
            this shouldContain "feilmelding melding"

            this shouldContain kravData1.kravId.toString()
            this shouldContain kravData1.saksnummerNAV
            this shouldContain kravData1.fagsystemId
            this shouldContain kravData1.tidspunktOpprettet.toString()
            this shouldContain kravData1.kravkode
            this shouldContain kravData1.kodeHjemmel
            this shouldContain kravData1.status
            this shouldContain kravData1.tidspunktSisteStatus.toString()

            this shouldContain kravData2.kravId.toString()
            this shouldContain kravData2.saksnummerNAV
            this shouldContain kravData2.fagsystemId
            this shouldContain kravData2.tidspunktOpprettet.toString()
            this shouldContain kravData2.kravkode
            this shouldContain kravData2.kodeHjemmel
            this shouldContain kravData2.status
            this shouldContain kravData2.tidspunktSisteStatus.toString()

        }

    }

    test("hentAvstemmingsRapportSomFil skal returnere avstemmingsrapporten på CSV format"){
        val kravliste = listOf(kravData1, kravData2)
        val dataSourceMock = mockk<DatabaseService> {
            every { getAllKravForAvstemming() } returns kravliste
        }

        AvstemmingService(dataSourceMock, mockk<FtpService>()).hentAvstemmingsRapportSomCSVFil().run {
             this shouldContain  "${kravData1.kravId},${kravData1.saksnummerNAV},${kravData1.tidspunktOpprettet},${kravData1.kravkode},${kravData1.kodeHjemmel},${kravData1.status},${kravData1.tidspunktSisteStatus}"
             this shouldContain  "${kravData2.kravId},${kravData2.saksnummerNAV},${kravData2.tidspunktOpprettet},${kravData2.kravkode},${kravData2.kodeHjemmel},${kravData2.status},${kravData2.tidspunktSisteStatus}"
        }
    }

})