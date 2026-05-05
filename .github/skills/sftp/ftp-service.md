# FtpService & JSchLogger

## JSchLogger — route JSch logs via TEAM_LOGS_MARKER

JSch logs credentials in its debug output. Route ERROR/FATAL through `TEAM_LOGS_MARKER` so they go to Team Logs instead of Loki:

```kotlin
class JSchLogger : Logger {
    private val logger = KotlinLogging.logger(JSch::class.java.name)

    override fun isEnabled(level: Int): Boolean =
        level == Logger.DEBUG && logger.isDebugEnabled

    override fun log(level: Int, message: String) {
        when (level) {
            Logger.ERROR -> logger.error(marker = TEAM_LOGS_MARKER) { message }
            Logger.FATAL -> logger.error(marker = TEAM_LOGS_MARKER) { message }
        }
    }
}
```

## FtpService

`FtpService` owns all SFTP operations. It downloads files sorted alphabetically, validates each via `FileValidator`, and returns only valid files as `FtpFil` objects. Invalid files are moved to `/inbound/feilfiler` and their errors are persisted.

```kotlin
data class FtpFil(
    val name: String,
    val content: List<String>,
    val kravLinjer: List<KravLinje>,
)

class FtpService(
    private val sftpConfig: SftpConfig = SftpConfig(),
    private val fileValidator: FileValidator = FileValidator(),
    private val databaseService: DatabaseService = DatabaseService(),
) {
    fun listFiles(directory: Directories = Directories.INBOUND): List<String> =
        sftpConfig.channel { con ->
            con.ls(directory.value).filterNot { it.attrs.isDir }.map { it.filename }
        }

    fun moveFile(fileName: String, from: Directories, to: Directories) {
        sftpConfig.channel { con ->
            val oldpath = "${from.value}${File.separator}$fileName"
            val newpath = "${to.value}${File.separator}$fileName"
            try {
                con.rename(oldpath, newpath)
            } catch (e: SftpException) {
                logger.error { "$fileName ble ikke flyttet fra $oldpath til $newpath: ${e.message}" }
                throw e
            }
        }
    }

    private fun downloadFiles(directory: Directories = Directories.INBOUND): Map<String, List<String>> =
        sftpConfig.channel { con ->
            try {
                listFiles(directory)
                    .sorted()                           // alphabetical order
                    .associateWith { filename ->
                        ByteArrayOutputStream().use { os ->
                            con.get("${directory.value}/$filename", os)
                            os.toString().lines().filter { it.isNotEmpty() }
                        }
                    }
            } catch (e: SftpException) {
                logger.error { "Filer i ${directory.value} ble ikke hentet" }
                logger.error(marker = TEAM_LOGS_MARKER) {
                    "Filer i ${directory.value} ble ikke hentet. Feilmelding: ${e.message}"
                }
                throw e
            }
        }

    suspend fun getValidatedFiles(directory: Directories = Directories.INBOUND): List<FtpFil> {
        val files = downloadFiles(directory)
        if (files.isEmpty()) return emptyList()

        return files.mapNotNull { (fileName, fileContent) ->
            when (val result = fileValidator.validateFile(fileContent, fileName)) {
                is ValidationResult.Success ->
                    FtpFil(fileName, fileContent, result.kravLinjer)
                is ValidationResult.Error -> {
                    moveFile(fileName, directory, Directories.FAILED)
                    result.messages.forEach { (key, msg) ->
                        databaseService.saveFileValidationError(fileName, "$key: $msg")
                    }
                    null
                }
            }
        }
    }
}
```
