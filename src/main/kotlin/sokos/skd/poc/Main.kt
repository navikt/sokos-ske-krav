package sokos.skd.poc

import sokos.skd.poc.config.PropertiesConfig
import sokos.skd.poc.database.DataSource
import sokos.skd.poc.maskinporten.MaskinportenAccessTokenClient
import kotlin.properties.Delegates

fun main(args: Array<String>) {
    println("Applikasjonen starter med følgende argumenter: ${args.joinToString()}")


    val applicationState = ApplicationState()
    val tokenProvider =
        MaskinportenAccessTokenClient(PropertiesConfig.MaskinportenClientConfig(), defaultHttpClient)
    val skdClient = SkdClient(tokenProvider, PropertiesConfig.SKEConfig().skeRestUrl)
    val skdService = SkdService(DataSource(PropertiesConfig.DbConfig()), skdClient)


    applicationState.ready = true
    HttpServer(applicationState, skdService).start()
}
class ApplicationState {
    var ready: Boolean by Delegates.observable(false) { _, _, newValue ->
    }
    var running: Boolean by Delegates.observable(false) { _, _, newValue ->
    }
}