# SftpListener — Integration Test Fixture

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
