package no.nav.sokos.ske.krav.listener

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.util.Base64

import io.kotest.core.listeners.TestListener
import io.kotest.core.spec.Spec
import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory
import org.apache.sshd.server.SshServer
import org.apache.sshd.server.auth.pubkey.AcceptAllPublickeyAuthenticator
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider
import org.apache.sshd.sftp.server.SftpSubsystemFactory

import no.nav.sokos.ske.krav.config.SftpConfig
import no.nav.sokos.ske.krav.config.SftpProperties
import no.nav.sokos.ske.krav.service.Directories
import no.nav.sokos.ske.krav.util.FtpTestUtil

object SftpListener : TestListener {
    private const val PORT = 58274
    private val rootDir: Path = Files.createTempDirectory("sftp-test")
    private val keyPair: KeyPair = generateKeyPair()
    private val privateKeyFile: File = createPrivateKeyFile(keyPair)
    private val sshd: SshServer = setupSshdServer()
    private var started = false

    val sftpProperties =
        SftpProperties(
            host = "localhost",
            username = "foo",
            privateKeyFilePath = privateKeyFile.absolutePath,
            privateKeyPassword = "",
            port = PORT,
        )
    private val sftpConfig = SftpConfig(sftpProperties)

    override suspend fun beforeSpec(spec: Spec) {
        if (!started) {
            sshd.start()
            started = true
            listOf("inbound", "inbound/feilfiler", "outbound").forEach {
                rootDir.resolve(it).toFile().mkdirs()
            }
        }
    }

    private fun setupSshdServer(): SshServer =
        SshServer.setUpDefaultServer().apply {
            port = PORT
            keyPairProvider = SimpleGeneratorHostKeyProvider(Files.createTempFile("sshd-host-key", ".ser"))
            publickeyAuthenticator = AcceptAllPublickeyAuthenticator.INSTANCE
            subsystemFactories = listOf(SftpSubsystemFactory())
            fileSystemFactory = VirtualFileSystemFactory(rootDir)
        }

    private fun generateKeyPair(): KeyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

    private fun createPrivateKeyFile(keyPair: KeyPair): File {
        val encoded = keyPair.private.encoded // PKCS#8 DER — JSch reads "BEGIN PRIVATE KEY" natively
        val base64 = Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(encoded)
        val pem = "-----BEGIN PRIVATE KEY-----\n$base64\n-----END PRIVATE KEY-----\n"
        return File("src/test/resources/privateKey").apply { writeText(pem) }
    }

    fun putFile(
        fileName: String,
        directory: Directories = Directories.INBOUND,
    ) = putFiles(listOf(fileName), directory)

    fun putFiles(
        fileNames: List<String>,
        directory: Directories = Directories.INBOUND,
    ) = sftpConfig.channel { con ->
        fileNames.forEach { fileName ->
            val destinationFileName = fileName.substringAfterLast("/")
            con.put(
                FtpTestUtil.fileAsString("/FtpFiler/$fileName").toByteArray().inputStream(),
                "${directory.value}/$destinationFileName",
            )
        }
    }

    fun clearDirectory(directory: Directories) {
        sftpConfig.channel { con ->
            val files = con.ls(directory.value).filter { !it.attrs.isDir }.map { it.filename }
            files.forEach { file ->
                con.rm("${directory.value}/$file")
            }
        }
    }
}
