package sokos.ske.krav

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import sokos.ske.krav.client.SkeClient
import sokos.ske.krav.config.PropertiesConfig
import sokos.ske.krav.config.commonConfig
import sokos.ske.krav.config.routingConfig
import sokos.ske.krav.metrics.Metrics
import sokos.ske.krav.metrics.installMetrics
import sokos.ske.krav.security.MaskinportenAccessTokenClient
import sokos.ske.krav.service.SkeService
import sokos.ske.krav.util.httpClient
import java.util.concurrent.TimeUnit
import kotlin.properties.Delegates

fun main() {
	val applicationState = ApplicationState()
	val tokenProvider = MaskinportenAccessTokenClient(PropertiesConfig.MaskinportenClientConfig(), httpClient)
	val skeClient = SkeClient(tokenProvider, PropertiesConfig.SKEConfig().skeRestUrl)
	val skeService = SkeService(skeClient)

	applicationState.ready = true
	HttpServer(applicationState, skeService).start()
}

class HttpServer(
	private val applicationState: ApplicationState,
	private val skeService: SkeService,
	port: Int = 8080,
) {
	init {
		Runtime.getRuntime().addShutdownHook(Thread {
			applicationState.running = false
			this.embeddedServer.stop(2, 20, TimeUnit.SECONDS)
		})
	}

	private val embeddedServer = embeddedServer(Netty, port) {
		installMetrics()
		applicationModule(applicationState, skeService)
	}

	fun start() {
		applicationState.running = true
		embeddedServer.start(wait = true)
	}
}

class ApplicationState {
	var ready: Boolean by Delegates.observable(false) { _, _, newValue ->
		if (!newValue) Metrics.appStateReadyFalse.increment()
		else Metrics.appStateReadyTrue.increment()
	}

	var running: Boolean by Delegates.observable(false) { _, _, newValue ->
		if (!newValue) Metrics.appStateRunningFalse.increment()
	}
}

private fun Application.applicationModule(
	applicationState: ApplicationState,
	skeService: SkeService,
) {
	commonConfig()
	routingConfig(applicationState, skeService)
}