package sokos.skd.poc.config

import java.util.*

object PropertiesConfig {
    private val props = Properties().also { it.load(this::class.java.classLoader.getResourceAsStream("ftpprops.local"))  }
    data class FtpConfig(
        val server:String = get("ftpServer"),
        val username:String = get("ftpUsername"),
        val password:String = get("ftpPassword"),
        val homeDirectory:String = get("ftpDirectory"),
        val port:Int = get("ftpPort").toInt()
    )

    fun get(property: String): String = props.getProperty(property)

}