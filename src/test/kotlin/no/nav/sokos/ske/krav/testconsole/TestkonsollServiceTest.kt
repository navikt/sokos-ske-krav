package no.nav.sokos.ske.krav.testconsole

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.justRun
import io.mockk.mockk

import no.nav.sokos.ske.krav.service.SkeService
import no.nav.sokos.ske.krav.service.StatusService

class TestkonsollServiceTest :
    FunSpec({
        test("behandleKrav skal bruke tjenestens ordinære behandlingsflyt") {
            val skeService = mockk<SkeService>()
            val statusService = mockk<StatusService>()
            coEvery { skeService.handleNewKrav(any()) } returns Unit
            val testkonsollService = TestkonsollService(skeService, statusService)

            testkonsollService.behandleKrav().message shouldBe "Kjørte ordinær behandling: mottaksstatus, resending, innlesing og utsending."
            coVerify { skeService.handleNewKrav(any()) }
        }

        test("lastOppFil skal delegere opplasting til testkonsollens SFTP-service") {
            val skeService = mockk<SkeService>()
            val statusService = mockk<StatusService>()
            val sftpService = mockk<TestkonsollSftpService>()
            justRun { sftpService.lastOppFil(any(), any()) }
            val testkonsollService = TestkonsollService(skeService, statusService, sftpService)

            testkonsollService.lastOppFil("testfil.txt", "innhold".toByteArray()).message shouldBe "Lastet opp testfil.txt til /inbound."
            io.mockk.verify { sftpService.lastOppFil("testfil.txt", "innhold".toByteArray()) }
        }

        test("hentMottaksstatus skal kjøre statusoppdateringen") {
            val skeService = mockk<SkeService>()
            val statusService = mockk<StatusService>()
            coEvery { statusService.getMottaksStatus() } returns Unit
            val testkonsollService = TestkonsollService(skeService, statusService)

            testkonsollService.hentMottaksstatus().message shouldBe "Henting av mottaksstatus er fullført. Se logger og database for oppdaterte statuser."
            coVerify { statusService.getMottaksStatus() }
        }
    })
