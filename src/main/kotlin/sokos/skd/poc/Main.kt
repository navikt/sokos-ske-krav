package sokos.skd.poc

import kotlin.properties.Delegates

fun main(args: Array<String>) {
    println("Applikasjonen starter med følgende argumenter: ${args.joinToString()}")

    val applicationState = ApplicationState()
    val configuration = Configuration()

    applicationState.ready = true
    HttpServer(applicationState, configuration).start()
}

class ApplicationState {
    var ready: Boolean by Delegates.observable(false) { _, _, newValue ->
    }
    var running: Boolean by Delegates.observable(false) { _, _, newValue ->
    }
}