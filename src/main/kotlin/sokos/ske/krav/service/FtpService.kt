package sokos.ske.krav.service

import com.jcraft.jsch.*
import mu.KotlinLogging
import sokos.ske.krav.config.PropertiesConfig
import sokos.ske.krav.domain.nav.KravLinje
import sokos.ske.krav.util.ValidationResult
import java.io.ByteArrayOutputStream

enum class Directories(val value: String) {
	OUTBOUND("/outbound"),
	INBOUND("/inbound"),
	FAILED("/inbound/test/feilfiler")
}

data class FtpFil(
	val name: String,
	val content: List<String>,
	val kravLinjer: List<KravLinje>
)

class FtpService(
    private val config: PropertiesConfig.FtpConfig = PropertiesConfig.FtpConfig(),
    val jsch: JSch = JSch()
)  {

	private val secureChannel: JSch = jsch.apply {
		addIdentity(config.privKey, config.keyPass)
		setKnownHosts(config.hostKey)
	}
	private val logger = KotlinLogging.logger {}

	private val session = secureChannel.getSession(config.username, config.server, config.port).apply {
		setConfig("PreferredAuthentications", "publickey")
	}

	private lateinit var sftpChannel: ChannelSftp

	init {
		JSch.setLogger(Slf4jLogger())
		connect()
	}

	private fun connect() {
		try {
			session.connect()
			sftpChannel = session.openChannel("sftp") as ChannelSftp
			sftpChannel.connect()
		} catch (e: JSchException) {
			logger.error("Feil i FTP oppkobling: ${e.message}")
		}
	}

	fun listAllFiles(directory: String): List<String> = sftpChannel.ls(directory).map { it.filename }

	fun listFiles(directory: Directories = Directories.INBOUND): List<String> =
		sftpChannel.ls(directory.value).map { it.filename }.filter { it.contains(".txt") || it.contains("NAVI") }

	fun moveFile(fileName: String, from: Directories, to: Directories) {
		sftpChannel.moveFile(fileName, from, to)
	}

	fun createFile(fileName: String, directory: Directories, content: String) =
		sftpChannel.createFile(fileName, directory, content)


    fun getValidatedFiles(directory: Directories = Directories.INBOUND, validator: (content: List<String>) -> ValidationResult): List<FtpFil> {
        val successFiles = mutableListOf<FtpFil>()
        downloadFiles(directory).map { entry ->
            when(val result: ValidationResult = validator(entry.value)){
                is ValidationResult.Success -> {
                    successFiles.add(FtpFil(entry.key, entry.value, result.kravLinjer))
                }
                is ValidationResult.Error -> {
                    moveFile(entry.key,directory, Directories.FAILED)
                }
            }
        }
        return successFiles
    }
    private fun downloadFiles(directory: Directories = Directories.INBOUND): Map<String, List<String>> = listFiles(directory).associateWith { sftpChannel.downloadFile("${directory.value}/$it") }
    private fun ChannelSftp.downloadFile(fileName: String): List<String>
    {
        val outputStream = ByteArrayOutputStream()
        try {
            get(fileName, outputStream)
        }catch (e: SftpException){
            logger.error{"Feil i henting av fil $fileName: ${e.message}"}
        }

        return String(outputStream.toByteArray()).split("\r?\n|\r".toRegex()).filter { it.isNotEmpty() }
    }
    private fun ChannelSftp.moveFile(fileName: String, from: Directories, to: Directories) {
        val oldpath = "${from.value}/${fileName}"
        val newpath = "${to.value}/${fileName}"

        try {
            rename(oldpath, newpath)
        } catch (e: NoSuchFileException) {
            logger.error{"Feil i flytting av fil fra $oldpath til $newpath: ${e.message}"}
        }
    }
    private fun ChannelSftp.createFile(fileName: String, directory: Directories, content: String){
        val path = "${directory.value}/$fileName"
        try {
            put(content.toByteArray().inputStream(), path)
        }catch (e: SftpException){
            logger.error{"Feil i opprettelse av fil $path: ${e.message}"}
        }

    }


}
