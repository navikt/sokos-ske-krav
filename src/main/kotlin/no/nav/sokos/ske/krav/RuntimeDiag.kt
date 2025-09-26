package no.nav.sokos.ske.krav

private val logger = mu.KotlinLogging.logger {}

object RuntimeDiag {
    fun logAndValidate() {
        val host =
            System.getenv("EXTERNAL_HOST")
                ?: System.getProperty("external.host")
                ?: "MISSING"
        val port =
            (
                System.getenv("EXTERNAL_PORT")
                    ?: System.getProperty("external.port")
                    ?: "0"
            ).toInt()

        logger.info("RuntimeDiag external.host=$host external.port=$port wd=${System.getProperty("user.dir")}")

        if (host == "MISSING" || port == 0) {
            logger.error("External service host/port not configured")
        }

        try {
            java.net.Socket().use { s ->
                s.connect(java.net.InetSocketAddress(host, port), 1500)
            }
            println("RuntimeDiag connectivity OK")
        } catch (e: Exception) {
            logger.error("Cannot reach $host:$port before starting scheduled tasks: ${e.message}")
        }
    }
}
