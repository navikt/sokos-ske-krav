package sokos.ske.krav.config

import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Logger
import com.jcraft.jsch.Session
import mu.KotlinLogging
import org.slf4j.LoggerFactory

class SftpConfig(
    private val sftpProperties: PropertiesConfig.SftpProperties = PropertiesConfig.SftpProperties(),
) {
    private val logger = KotlinLogging.logger("secureLogger")
    private val jsch: JSch =
        JSch().apply {
            JSch.setLogger(JSchLogger())
            addIdentity(sftpProperties.privateKey, sftpProperties.privateKeyPassword)
        }

    fun <T> channel(operation: (ChannelSftp) -> T): T {
        var session: Session? = null
        var sftpChannel: ChannelSftp? = null

        try {
            session =
                jsch.getSession(sftpProperties.username, sftpProperties.host, sftpProperties.port).apply {
                    setConfig("StrictHostKeyChecking", "no")
                    connect()
                }
            sftpChannel = (session.openChannel("sftp") as ChannelSftp).apply { connect() }
            return operation(sftpChannel)
        } finally {
            sftpChannel?.disconnect()
            session?.disconnect()
        }
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
