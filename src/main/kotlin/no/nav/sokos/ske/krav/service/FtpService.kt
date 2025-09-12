package no.nav.sokos.ske.krav.service

import java.io.ByteArrayOutputStream
import java.io.File

import com.jcraft.jsch.SftpException
import mu.KotlinLogging

import no.nav.sokos.ske.krav.config.SftpConfig

enum class Directories(
    val value: String,
) {
    OUTBOUND("/outbound"),
    INBOUND("/inbound"),
    FAILED("/inbound/feilfiler"),
}

private val logger = KotlinLogging.logger("secureLogger")

class FtpService(
    private val sftpConfig: SftpConfig = SftpConfig(),
) {
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

    fun downloadFiles(directory: Directories = Directories.INBOUND): Map<String, List<String>> =
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
                logger.error { "Filer i ${directory.value} ble ikke hentet. Feilmelding: ${e.message}" }
                throw e
            }
        }

    fun listFiles(directory: Directories = Directories.INBOUND): List<String> =
        sftpConfig.channel { con ->
            con.ls(directory.value).filterNot { it.attrs.isDir }.map { it.filename }
        }
}
