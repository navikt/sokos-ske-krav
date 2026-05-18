package no.nav.sokos.ske.krav.service

import java.io.ByteArrayOutputStream
import java.io.File
import javax.sql.DataSource

import com.jcraft.jsch.SftpException
import com.sun.org.apache.xml.internal.serializer.utils.Utils.messages
import jdk.internal.joptsimple.internal.Messages.message
import mu.KotlinLogging

import no.nav.sokos.ske.krav.config.PostgresDataSource
import no.nav.sokos.ske.krav.config.SftpConfig
import no.nav.sokos.ske.krav.config.TEAM_LOGS_MARKER
import no.nav.sokos.ske.krav.copybook.KravLinje
import no.nav.sokos.ske.krav.repository.FilValideringsfeilRepository
import no.nav.sokos.ske.krav.util.transaction
import no.nav.sokos.ske.krav.validation.FileValidator
import no.nav.sokos.ske.krav.validation.ValidationResult

enum class Directories(
    val value: String,
) {
    OUTBOUND("/outbound"),
    INBOUND("/inbound"),
    FAILED("/inbound/feilfiler"),
}

data class FtpFil(
    val name: String,
    val kravLinjer: List<KravLinje>,
)

class FtpService(
    private val dataSource: DataSource = PostgresDataSource.dataSource,
    private val sftpConfig: SftpConfig = SftpConfig(),
    private val fileValidator: FileValidator = FileValidator(),
    private val filValideringsfeilRepository: FilValideringsfeilRepository = FilValideringsfeilRepository.instance,
) {
    private val logger = KotlinLogging.logger { }

    fun listFiles(directory: Directories = Directories.INBOUND): List<String> =
        sftpConfig.channel { con ->
            con.ls(directory.value).filterNot { it.attrs.isDir }.map { it.filename }
        }

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

    private fun downloadFiles(directory: Directories = Directories.INBOUND): Map<String, List<String>> =
        sftpConfig.channel { con ->
            try {
                listFiles(directory)
                    .sorted()
                    .associateWith { filename ->
                        ByteArrayOutputStream().use { os ->
                            con.get("${directory.value}/$filename", os)
                            os.toString().lines().filter { it.isNotEmpty() }
                        }
                    }
            } catch (e: SftpException) {
                logger.error { "Filer i ${directory.value} ble ikke hentet" }
                logger.error(marker = TEAM_LOGS_MARKER) { "Filer i ${directory.value} ble ikke hentet. Feilmelding: ${e.message}" }
                throw e
            }
        }

    suspend fun getValidatedFiles(directory: Directories = Directories.INBOUND): List<FtpFil> {
        val files = downloadFiles(directory)
        if (files.isEmpty()) return emptyList()

        return files.mapNotNull { (fileName, fileContent) ->
            when (val validationResult = fileValidator.validateFile(fileContent, fileName)) {
                is ValidationResult.Success -> {
                    FtpFil(fileName, validationResult.kravLinjer)
                }

                is ValidationResult.Error -> {
                    handleValidationError(fileName, validationResult.messages, directory)
                    null
                }
            }
        }
    }

    private fun handleValidationError(
        fileName: String,
        errorMessages: List<Pair<String, String>>,
        directory: Directories,
    ) {
        moveFile(fileName, directory, Directories.FAILED)

        dataSource.transaction { session ->
            errorMessages.forEach { (errorKey, message) ->
                filValideringsfeilRepository.insertFilValideringsfeil(session, fileName, "$errorKey: $message")
            }
        }
    }
}
