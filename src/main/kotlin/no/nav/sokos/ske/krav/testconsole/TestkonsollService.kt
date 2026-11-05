package no.nav.sokos.ske.krav.testconsole

import kotlin.time.Duration.Companion.ZERO

import no.nav.sokos.ske.krav.service.SkeService
import no.nav.sokos.ske.krav.service.StatusService

class TestkonsollService(
    private val skeService: SkeService,
    private val statusService: StatusService,
    private val testkonsollSftpService: TestkonsollSftpService? = null,
) {
    data class ActionResult(
        val message: String,
    )

    fun lastOppFil(
        filnavn: String,
        innhold: ByteArray,
    ): ActionResult {
        (testkonsollSftpService ?: TestkonsollSftpService()).lastOppFil(filnavn, innhold)
        return ActionResult("Lastet opp $filnavn til /inbound.")
    }

    suspend fun behandleKrav(): ActionResult {
        skeService.handleNewKrav(ZERO)
        return ActionResult("Kjørte ordinær behandling: mottaksstatus, resending, innlesing og utsending.")
    }

    suspend fun hentMottaksstatus(): ActionResult {
        statusService.getMottaksStatus()
        return ActionResult("Henting av mottaksstatus er fullført. Se logger og database for oppdaterte statuser.")
    }
}
