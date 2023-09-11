package sokos.skd.poc

import sokos.skd.poc.client.SkeClient
import sokos.skd.poc.client.defaultHttpClient
import sokos.skd.poc.config.PropertiesConfig
import sokos.skd.poc.maskinporten.MaskinportenAccessTokenClient
import sokos.skd.poc.service.SkeService
import kotlin.properties.Delegates

fun main() {

    val applicationState = ApplicationState()
    val tokenProvider =
        MaskinportenAccessTokenClient(PropertiesConfig.MaskinportenClientConfig(), defaultHttpClient)
    val skeClient = SkeClient(tokenProvider, PropertiesConfig.SKEConfig().skeRestUrl)
    val skeService = SkeService(skeClient)


    applicationState.ready = true
    HttpServer(applicationState, skeService).start()
}
class ApplicationState {
    var ready: Boolean by Delegates.observable(false) { _, _, newValue ->
    }
    var running: Boolean by Delegates.observable(false) { _, _, newValue ->
    }
}