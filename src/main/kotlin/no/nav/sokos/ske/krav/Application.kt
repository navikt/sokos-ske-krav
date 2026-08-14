package no.nav.sokos.ske.krav

import java.time.LocalDate
import javax.sql.DataSource

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

import no.nav.sokos.ske.krav.config.ApplicationState
import no.nav.sokos.ske.krav.config.PostgresDataSource
import no.nav.sokos.ske.krav.config.PropertiesConfig
import no.nav.sokos.ske.krav.config.PropertiesConfig.timerConfig
import no.nav.sokos.ske.krav.config.TEAM_LOGS_MARKER
import no.nav.sokos.ske.krav.config.applicationLifecycleConfig
import no.nav.sokos.ske.krav.config.commonConfig
import no.nav.sokos.ske.krav.config.loadConfig
import no.nav.sokos.ske.krav.config.routingConfig
import no.nav.sokos.ske.krav.config.securityConfig
import no.nav.sokos.ske.krav.domain.StonadsType
import no.nav.sokos.ske.krav.metrics.Metrics
import no.nav.sokos.ske.krav.repository.FeilmeldingRepository
import no.nav.sokos.ske.krav.repository.FilValideringsfeilRepository
import no.nav.sokos.ske.krav.repository.KravRepository
import no.nav.sokos.ske.krav.service.Frontend
import no.nav.sokos.ske.krav.service.SkeService
import no.nav.sokos.ske.krav.util.transaction

fun main() {
    embeddedServer(Netty, port = 8080, module = Application::module).start(true)
}

private val logger = mu.KotlinLogging.logger {}

@OptIn(Frontend::class)
internal fun Application.module() {
    PropertiesConfig.load(loadConfig())

    val useAuthentication = PropertiesConfig.applicationProperties.useAuthentication
    val applicationState = ApplicationState()
    val skeService = SkeService()

    commonConfig()
    applicationLifecycleConfig(applicationState)
    securityConfig()
    routingConfig(useAuthentication, applicationState)

    if (!PropertiesConfig.isLocal) {
        PostgresDataSource.migrate()
    }

    StonadsType.entries
        .flatMap { it.kravKoder }
        .distinct()
        .forEach { kravKode ->
            Metrics.registerKravKodeCounter(kravKode)
        }

    logger.info { "Application started with timerConfig: $timerConfig" }

    if (!timerConfig.useTimer) {
        logger.info { "Timer er deaktivert, ske-krav kommer ikke til å gjøre noe" }
        return
    }

    logger.debug("Debug visible here")
    logger.info { "Info visible here" }
    logger.error { "Error visible here" }
    logger.warn { "Warning visible here" }

    launchJob(skeService::handleNewKrav, timerConfig.schedulerIntervalPeriod)
    launchJob(skeService::checkForStangendeKrav, 24.hours)
    launchJob(::deleteOldData, 24.hours)
}

private fun deleteOldData(dataSource: DataSource = PostgresDataSource.dataSource) {
    dataSource.transaction { session ->
        val threshold = LocalDate.now().minusYears(10)
        FilValideringsfeilRepository.instance.deleteOldFilValideringsfeil(session, threshold).reportDeletedData("filvalideringsfeil(er)")
        FeilmeldingRepository.instance.deleteOldFeilmeldinger(session, threshold).reportDeletedData("feilmelding(er)")
        KravRepository.instance.deleteOldKrav(session, threshold).reportDeletedData("krav")
    }
}

private fun Int.reportDeletedData(name: String) {
    takeIf { it != 0 }?.let {
        logger.info { "Slettet $it $name." }
    }
}

private fun CoroutineScope.launchJob(
    function: suspend () -> Unit,
    delayDuration: Duration,
) = launch {
    while (true) {
        try {
            function()
            delay(delayDuration)
        } catch (_: CancellationException) {
            logger.info { "Scheduled task cancelled" }
            break
        } catch (e: Exception) {
            logger.error(marker = TEAM_LOGS_MARKER) { "Unhandled exception in scheduled task ${e.message}" }
            delay(delayDuration / 2)
        }
    }
}
