package no.nav.sokos.ske.krav.config

import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Logger
import com.jcraft.jsch.Session
import mu.KotlinLogging.logger

class SftpConfig(
    private val sftpProperties: SftpProperties = PropertiesConfig.sftpProperties,
) {
    val logger = logger {}
    private val jsch: JSch =
        JSch().apply {
            JSch.setLogger(JSchLogger())

            addIdentity(sftpProperties.privateKeyFilePath, sftpProperties.trimmedPrivateKeyPassword)
        }

    fun <T> channel(operation: (ChannelSftp) -> T): T {
        var session: Session? = null
        var sftpChannel: ChannelSftp? = null

        try {
            session =
                jsch.getSession(sftpProperties.trimmedUsername, sftpProperties.host, sftpProperties.port).apply {
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
    private val logger = logger(JSch::class.java.name)

    override fun isEnabled(level: Int): Boolean = level == Logger.DEBUG && logger.isDebugEnabled

    override fun log(
        level: Int,
        message: String,
    ) {
        when (level) {
            Logger.ERROR -> logger.error(marker = TEAM_LOGS_MARKER) { message }
            Logger.FATAL -> logger.error(marker = TEAM_LOGS_MARKER) { message }
        }
    }
}
