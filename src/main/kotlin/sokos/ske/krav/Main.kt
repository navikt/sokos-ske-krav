package sokos.ske.krav

import sokos.ske.krav.client.SkeClient
import sokos.ske.krav.client.defaultHttpClient
import sokos.ske.krav.config.PropertiesConfig
import sokos.ske.krav.maskinporten.MaskinportenAccessTokenClient
import sokos.ske.krav.service.SkeService
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