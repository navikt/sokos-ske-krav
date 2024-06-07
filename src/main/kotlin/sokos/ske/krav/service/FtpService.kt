package sokos.ske.krav.service

import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.Session
import com.jcraft.jsch.SftpException
import mu.KotlinLogging
import sokos.ske.krav.config.PropertiesConfig
import sokos.ske.krav.config.SftpConfig
import sokos.ske.krav.domain.nav.KravLinje
import sokos.ske.krav.validation.FileValidator
import sokos.ske.krav.validation.ValidationResult
import java.io.ByteArrayOutputStream
import java.io.File


enum class Directories(val value: String) {
    OUTBOUND("/outbound"),
    INBOUND("/inbound"),
    FAILED("/inbound/feilfiler")
}

data class FtpFil(
    val name: String,
    val content: List<String>,
    val kravLinjer: List<KravLinje>
)
class FtpService (
    private val config: PropertiesConfig.SftpProperties = PropertiesConfig.SftpProperties(),
    private val sftpSession: Session = SftpConfig(config).createSftpConnection(),

    ) {
        private val logger = KotlinLogging.logger("secureLogger")
        val session get() =sftpSession
        private fun getSftpChannel(): ChannelSftp {
            val channelSftp = sftpSession.openChannel("sftp") as ChannelSftp
            return channelSftp.apply {
                connect()
            }
        }


        fun listFiles(directory: Directories = Directories.INBOUND): List<String> =
            getSftpChannel().ls(directory.value).filter { !it.attrs.isDir  }.map { it.filename }

        fun moveFile(fileName: String, from: Directories, to: Directories) {
            getSftpChannel().apply {
                val oldpath = "${from.value}${File.separator}$fileName"
                val newpath = "${to.value}${File.separator}$fileName"

                try {
                    rename(oldpath, newpath)
                    logger.debug { "$fileName ble flyttet fra mappen ${from.value} til mappen ${to.value}" }
                } catch (e: SftpException) {
                    logger.error {
                        "$fileName ble ikke flyttet fra mappe $oldpath til mappe $newpath: ${e.message}"
                    }
                    throw e
                } finally {
                    exit()
                }
            }
        }
        private fun downloadFiles(directory: Directories = Directories.INBOUND): Map<String, List<String>> {
            var fileName = ""
            getSftpChannel().apply {
                try {
                    return this.ls("${directory.value}/*")
                        .filter { !it.attrs.isDir }
                        .map { it.filename }
                        .sorted()
                        .associateWith {
                            fileName = "${directory.value}/$it"
                            val outputStream = ByteArrayOutputStream()
                            logger.debug { "$fileName ble lastet ned fra mappen $directory" }
                            get(fileName, outputStream)
                            String(outputStream.toByteArray()).split("\r?\n|\r".toRegex()).filter { file -> file.isNotEmpty() }
                        }
                } catch (e: SftpException) {
                    logger.error { "$fileName ble ikke hentet. Feilmelding: ${e.message}" }
                    throw e
                } finally {
                    exit()
                }
            }
        }

        fun getValidatedFiles(directory: Directories = Directories.INBOUND): List<FtpFil> {
            val successFiles = mutableListOf<FtpFil>()
            val files = downloadFiles(directory)
            if (files.isEmpty()) return emptyList()

            files.map { entry ->
                when (val result: ValidationResult = FileValidator.validateFile(entry.value, entry.key)) {
                    is ValidationResult.Success -> {
                        successFiles.add(FtpFil(entry.key, entry.value, result.kravLinjer))
                    }
                    is ValidationResult.Error -> {
                        moveFile(entry.key, directory, Directories.FAILED)
                    }
                }
            }
            return successFiles
        }

}

