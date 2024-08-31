package sokos.ske.krav.config

import com.jcraft.jsch.JSch
import com.jcraft.jsch.Logger
import com.jcraft.jsch.Session
import mu.KotlinLogging
import org.slf4j.LoggerFactory

class SftpConfig(
    private val sftpProperties: PropertiesConfig.SftpProperties = PropertiesConfig.SftpProperties(),
) {
    private val logger = KotlinLogging.logger {}

    fun createSftpConnection(): Session =
        JSch().apply {
            JSch.setLogger(JSchLogger())
            addIdentity(sftpProperties.privateKey, sftpProperties.privateKeyPassword)
        }.run {
            logger.info { "Oppretter connection med privat nøkkel på host: ${sftpProperties.host}:${sftpProperties.port}" }
            getSession(sftpProperties.username, sftpProperties.host, sftpProperties.port)
        }.also {
            it.setConfig("StrictHostKeyChecking", "no")
            it.connect()
            logger.info { "Åpner session på host: ${sftpProperties.host}:${sftpProperties.port}" }
        }
}

class JSchLogger : Logger {
    private val logger = LoggerFactory.getLogger(JSch::class.java)

    override fun isEnabled(level: Int): Boolean = level == Logger.DEBUG && logger.isDebugEnabled

    override fun log(
        level: Int,
        message: String,
    ) {
        when (level) {
            Logger.DEBUG -> logger.debug(message)
            Logger.INFO -> logger.info(message)
            Logger.WARN -> logger.warn(message)
            Logger.ERROR -> logger.error(message)
            Logger.FATAL -> logger.error(message)
        }
    }
}