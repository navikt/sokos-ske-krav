package sokos.ske.krav.service

import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import sokos.ske.krav.config.PropertiesConfig
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

class FtpService()  {
    private val config = PropertiesConfig.FtpConfig()
    fun connect(): ChannelSftp {
        val secureChannel = JSch()
        secureChannel.addIdentity(config.username, config.password.toByteArray(), null, config.keyPass.toByteArray())
        secureChannel.setKnownHosts(ByteArrayInputStream(config.hostKey.toByteArray(StandardCharsets.UTF_8)))
        val session = secureChannel.getSession(config.username, config.server, config.port)

            session.setConfig("PreferredAuthentications", "publickey")
            session.setConfig("StrictHostKeyChecking", "no")
            session.connect(5000)


        val sftpChannel: ChannelSftp = session.openChannel("sftp") as ChannelSftp
        try{
            sftpChannel.connect()
        }catch (e: Exception){
            println("Exception i channel connect: ${e.message}")
        }

        return sftpChannel
    }

    fun listFiles(sftpChannel: ChannelSftp): List<String> {

        try{
            println(sftpChannel.ls("/").toList())
            println(sftpChannel.ls("/inbound/").toList())
            return sftpChannel.ls("/inbound/test").toList() as List<String>
        }catch (e: Exception){
            println("Exception i channel ls: ${e.message}")
        }
            return emptyList()
    }

    fun getFiles(sftpChannel: ChannelSftp): MutableList<String>? {

        try {
            val inputStream: InputStream = sftpChannel.get("/inbound/test/fil1.txt")
            val inputStreamReader = InputStreamReader(inputStream, StandardCharsets.ISO_8859_1)
            val bufferedReader = BufferedReader(inputStreamReader)

            val strings = bufferedReader.lines().toList()

            println("strings = $strings")
            return strings

        } catch (e: Exception) {
            println("Exception i channel get: ${e.message}")
        }
        return mutableListOf(":(")
    }
}