package sokos.skd.poc
import sokos.ur.iso.HttpServer
import kotlin.properties.Delegates

fun main(args: Array<String>) {
    println("Applikasjonen starter med følgende argumenter: ${args.joinToString()}")

    val applicationState = ApplicationState()


    applicationState.ready = true
    HttpServer(applicationState).start()
}

class ApplicationState {
    var ready: Boolean by Delegates.observable(false) { _, _, newValue ->
    }
    var running: Boolean by Delegates.observable(false) { _, _, newValue ->
    }
}