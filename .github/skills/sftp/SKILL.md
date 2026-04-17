---
name: sftp
description: "JSch SFTP-mønstre: SftpConfig.channel {} livssyklus, FtpService fil-operasjoner, Directories enum og SftpListener integrasjonstest-fixture"
---

# SFTP Patterns

SFTP-mønstre med JSch: SftpConfig.channel {}, FtpService, Directories og SftpListener for testing.

> This project uses JSch (`com.github.mwiede:jsch`) for SFTP. Authentication is RSA private key only — no password auth. All SFTP operations go through `SftpConfig.channel {}`, which opens a session + channel and closes them in `finally`.

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

## Directories enum

Use the `Directories` enum for all path references — never hardcode strings:

```kotlin
enum class Directories(val value: String) {
    OUTBOUND("/outbound"),
    INBOUND("/inbound"),
    FAILED("/inbound/feilfiler"),
}
```

## Sub-files

- See [ftp-service.md](ftp-service.md) for FtpService (file operations, FtpFil data class) and JSchLogger.
- See [sftp-listener.md](sftp-listener.md) for SftpListener integration test fixture.

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
