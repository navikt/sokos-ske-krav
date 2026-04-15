---
applyTo: "**/service/Ftp*.kt,**/config/Sftp*.kt,**/listener/SftpListener.kt"
---

SFTP-mønstre med JSch: SftpConfig.channel {}, FtpService, Directories og SftpListener for testing.

> This project uses JSch (`com.github.mwiede:jsch`) for SFTP. Authentication is RSA private key only — no password auth. All SFTP operations go through `SftpConfig.channel {}`, which opens a session + channel and closes them in `finally`.

# SFTP Patterns

## SftpConfig — session/channel lifecycle

Never open `JSch` sessions or `ChannelSftp` channels directly in service code. Always use `SftpConfig.channel {}`:

```kotlin
class SftpConfig(
    private val sftpProperties: SftpProperties = PropertiesConfig.sftpProperties,
) {
    private val jsch: JSch =
        JSch().apply {
            JSch.setLogger(JSchLogger())
            addIdentity(
                sftpProperties.privateKeyFilePath,
                sftpProperties.trimmedPrivateKeyPassword,
            )
        }

    fun <T> channel(operation: (ChannelSftp) -> T): T {
        var session: Session? = null
        var sftpChannel: ChannelSftp? = null
        try {
            session = jsch.getSession(
                sftpProperties.trimmedUsername,
                sftpProperties.host,
                sftpProperties.port,
            ).apply {
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
```

`SftpProperties.trimmedUsername` and `trimmedPrivateKeyPassword` strip whitespace from Vault-sourced values — always use the trimmed getters.

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

## Directories enum

Use the `Directories` enum for all path references — never hardcode strings:

```kotlin
enum class Directories(val value: String) {
    OUTBOUND("/outbound"),
    INBOUND("/inbound"),
    FAILED("/inbound/feilfiler"),
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

## SftpListener — integration test fixture

`SftpListener` starts an in-process Apache SSHD server with a temp filesystem. Inject it via `extensions(SftpListener)`:

```kotlin
object SftpListener : TestListener {
    private const val PORT = 58274
    private val rootDir: Path = Files.createTempDirectory("sftp-test")
    private val keyPair: KeyPair = KeyPairGenerator.getInstance("RSA")
        .apply { initialize(2048) }.generateKeyPair()
    private val privateKeyFile: File = createPrivateKeyFile(keyPair)
    private val sshd: SshServer = setupSshdServer()

    val sftpProperties = SftpProperties(
        host = "localhost",
        username = "foo",
        privateKeyFilePath = privateKeyFile.absolutePath,
        privateKeyPassword = "",
        port = PORT,
    )
    private val sftpConfig = SftpConfig(sftpProperties)

    override suspend fun beforeSpec(spec: Spec) {
        sshd.start()
        listOf("inbound", "inbound/feilfiler", "outbound").forEach {
            rootDir.resolve(it).toFile().mkdirs()
        }
    }
}
```

Upload a test file via the shared `sftpConfig`:

```kotlin
sftpConfig.channel { con ->
    con.put(inputStream, "/inbound/testfile.txt")
}
```

## Boundaries

### ✅ Always

- Use `SftpConfig.channel {}` — never open JSch sessions directly in service or test code
- Use `Directories` enum for paths
- Use `sftpProperties.trimmedUsername` and `trimmedPrivateKeyPassword`
- Route JSch ERROR/FATAL logs through `TEAM_LOGS_MARKER`
- Download files sorted alphabetically (`listFiles().sorted()`)
- Move failed files to `Directories.FAILED` before persisting validation errors

### 🚫 Never

- Set `StrictHostKeyChecking = yes` in production (not supported by the SFTP server)
- Keep sessions open across multiple operations (open/close per `channel {}` call)
- Log JSch session details (host, key material) outside `TEAM_LOGS_MARKER`

