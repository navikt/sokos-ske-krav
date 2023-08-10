package sokos.skd.poc

import io.ktor.server.application.*
import io.ktor.server.plugins.callid.*
import io.ktor.server.plugins.callloging.*
import mu.KotlinLogging
import org.slf4j.event.Level
import java.util.*


fun Application.installCommonFeatures(){
    install(CallId) {
        header("nav-call-id")
        generate { UUID.randomUUID().toString() }
        verify { it.isNotEmpty() }
    }
    install(CallLogging) {
        logger = KotlinLogging.logger {}
        level = Level.INFO
        callIdMdc("x-correlation-id")
        disableDefaultColors()
    }
}
