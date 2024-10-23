package sokos.ske.krav.service

import com.jcraft.jsch.SftpException
import mu.KotlinLogging
import sokos.ske.krav.config.SftpConfig
import sokos.ske.krav.domain.nav.KravLinje
import sokos.ske.krav.validation.FileValidator
import sokos.ske.krav.validation.ValidationResult
import java.io.ByteArrayOutputStream
import java.io.File

enum class Directories(
    val value: String,
) {
    OUTBOUND("/outbound"),
    INBOUND("/inbound"),
    FAILED("/inbound/feilfiler"),
}

data class FtpFil(
    val name: String,
    val content: List<String>,
    val kravLinjer: List<KravLinje>,
)

class FtpService(
    private val sftpConfig: SftpConfig = SftpConfig(),
    private val fileValidator: FileValidator = FileValidator(),
) {
    private val logger = KotlinLogging.logger("secureLogger")

    fun listFiles(directory: Directories = Directories.INBOUND): List<String> = sftpConfig.channel { con -> con.ls(directory.value).filter { !it.attrs.isDir }.map { it.filename } }

    fun moveFile(
        fileName: String,
        from: Directories,
        to: Directories,
    ) {
        sftpConfig.channel { con ->
            val oldpath = "${from.value}${File.separator}$fileName"
            val newpath = "${to.value}${File.separator}$fileName"

            try {
                con.rename(oldpath, newpath)
            } catch (e: SftpException) {
                logger.error {
                    "$fileName ble ikke flyttet fra mappe $oldpath til mappe $newpath: ${e.message}"
                }
                throw e
            }
        }
    }

    private fun downloadFiles(directory: Directories = Directories.INBOUND): Map<String, List<String>> {
        var fileName = ""
        return sftpConfig.channel { con ->
            try {
                con
                    .ls("${directory.value}/*")
                    .filter { !it.attrs.isDir }
                    .map { it.filename }
                    .sorted()
                    .associateWith {
                        fileName = "${directory.value}/$it"
                        val outputStream = ByteArrayOutputStream()
                        con.get(fileName, outputStream)
                        String(outputStream.toByteArray()).lines().filter { file -> file.isNotEmpty() }
                    }
            } catch (e: SftpException) {
                logger.error { "$fileName ble ikke hentet. Feilmelding: ${e.message}" }
                throw e
            }
        }
    }

    suspend fun getValidatedFiles(directory: Directories = Directories.INBOUND): List<FtpFil> {
        val successFiles = mutableListOf<FtpFil>()
        val files = downloadFiles(directory)
        if (files.isEmpty()) return emptyList()

        files.map { entry ->
            when (val result: ValidationResult = fileValidator.validateFile(entry.value, entry.key)) {
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
