package sokos.skd.poc

import kotlinx.coroutines.runBlocking
import sokos.skd.poc.maskinporten.MaskinportenAccessTokenClient
import kotlin.properties.Delegates

fun main(args: Array<String>) = runBlocking{
    println("Applikasjonen starter med følgende argumenter: ${args.joinToString()}")

    val applicationState = ApplicationState()
    val configuration = Configuration()
    val tokenProvider = MaskinportenAccessTokenClient(configuration.maskinportenClientConfig, defaultHttpClient)
    val skdClient = SkdClient(tokenProvider, configuration.skdRestUrl)
    val skdService = SkdService(skdClient)


    applicationState.ready = true
    HttpServer(applicationState, skdService, configuration).start()
}

class ApplicationState {
    var ready: Boolean by Delegates.observable(false) { _, _, newValue ->
    }
    var running: Boolean by Delegates.observable(false) { _, _, newValue ->
    }
}