package no.nav.sokos.ske.krav

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

        println("RuntimeDiag external.host=$host external.port=$port wd=${System.getProperty("user.dir")}")

        if (host == "MISSING" || port == 0) {
            error("External service host/port not configured")
        }

        try {
            java.net.Socket().use { s ->
                s.connect(java.net.InetSocketAddress(host, port), 1500)
            }
            println("RuntimeDiag connectivity OK")
        } catch (e: Exception) {
            error("Cannot reach $host:$port before starting scheduled tasks: ${e.message}")
        }
    }
}
