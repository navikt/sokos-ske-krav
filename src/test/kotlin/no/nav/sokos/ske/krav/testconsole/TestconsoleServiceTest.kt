package no.nav.sokos.ske.krav.testconsole

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.justRun
import io.mockk.mockk

import no.nav.sokos.ske.krav.service.SkeService
import no.nav.sokos.ske.krav.service.StatusService

class TestconsoleServiceTest :
    FunSpec({
        test("behandleKrav skal bruke tjenestens ordinære behandlingsflyt") {
            val skeService = mockk<SkeService>()
            val statusService = mockk<StatusService>()
            coEvery { skeService.handleNewKrav(any()) } returns Unit
            val testconsoleService = TestconsoleService(skeService, statusService)

            testconsoleService.behandleKrav().message shouldBe "Kjørte ordinær behandling: mottaksstatus, resending, innlesing og utsending."
            coVerify { skeService.handleNewKrav(any()) }
        }

        test("lastOppFil skal delegere opplasting til testkonsollens SFTP-service") {
            val skeService = mockk<SkeService>()
            val statusService = mockk<StatusService>()
            val sftpService = mockk<TestconsoleSftpService>()
            justRun { sftpService.lastOppFil(any(), any()) }
            val testconsoleService = TestconsoleService(skeService, statusService, sftpService)

            testconsoleService.lastOppFil("testfil.txt", "innhold".toByteArray()).message shouldBe "Lastet opp testfil.txt til /inbound."
            io.mockk.verify { sftpService.lastOppFil("testfil.txt", "innhold".toByteArray()) }
        }

        test("hentMottaksstatus skal kjøre statusoppdateringen") {
            val skeService = mockk<SkeService>()
            val statusService = mockk<StatusService>()
            coEvery { statusService.getMottaksStatus() } returns Unit
            val testconsoleService = TestconsoleService(skeService, statusService)

            testconsoleService.hentMottaksstatus().message shouldBe "Henting av mottaksstatus er fullført. Se logger og database for oppdaterte statuser."
            coVerify { statusService.getMottaksStatus() }
        }
    })
