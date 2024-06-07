package sokos.ske.krav.util.containers

import com.github.dockerjava.api.model.ExposedPort
import com.github.dockerjava.api.model.PortBinding
import com.github.dockerjava.api.model.Ports
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.Session
import io.kotest.core.listeners.TestListener
import io.kotest.core.spec.Spec
import org.testcontainers.containers.GenericContainer
import org.testcontainers.images.builder.Transferable
import org.testcontainers.shaded.org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.testcontainers.shaded.org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.testcontainers.shaded.org.bouncycastle.crypto.util.OpenSSHPrivateKeyUtil
import org.testcontainers.shaded.org.bouncycastle.crypto.util.OpenSSHPublicKeyUtil
import org.testcontainers.shaded.org.bouncycastle.util.io.pem.PemObject
import org.testcontainers.shaded.org.bouncycastle.util.io.pem.PemWriter
import sokos.ske.krav.config.PropertiesConfig
import sokos.ske.krav.service.Directories
import sokos.ske.krav.util.FtpTestUtil.fileAsString
import java.io.File
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID


object SftpListener : TestListener{
    override suspend fun beforeSpec(spec: Spec)  =  SftpContainer.container.start()
    override suspend fun afterSpec(spec: Spec)  =  SftpContainer.container.stop()
}

object SftpContainer {

    private val keyPair = Ed25519KeyPairGenerator().run {
        init(Ed25519KeyGenerationParameters(SecureRandom()))
        generateKeyPair()
    }

    private val privateKeyFile = File("src/test/resources/privateKey").apply {
        outputStream().use {
            PemWriter(writer()).use { pw ->
                pw.writeObject(PemObject("OPENSSH PRIVATE KEY", OpenSSHPrivateKeyUtil.encodePrivateKey(keyPair.private)))
            }
        }
    }

    private val transferablePublicKey = Transferable.of("ssh-ed25519 ${
            Base64.getEncoder().encodeToString(OpenSSHPublicKeyUtil.encodePublicKey(keyPair.public))}")

    internal val sftpProperties =
        PropertiesConfig.SftpProperties(
            host = "localhost",
            username = "foo",
            privateKey = privateKeyFile.absolutePath,
            privateKeyPassword = "pass",
            port = 5678,
        )

    internal val container =
        GenericContainer("atmoz/sftp:alpine")
            .withCopyToContainer(transferablePublicKey, "/home/foo/.ssh/keys/id_rsa.pub")
            .withExposedPorts(22)
            .withCreateContainerCmdModifier { cmd ->
                cmd.hostConfig!!.withPortBindings(PortBinding(Ports.Binding.bindPort(5678), ExposedPort(22)))
                cmd.withName(UUID.randomUUID().toString())
            }
            .withCommand("foo::::inbound,inbound/feilfiler,outbound")

    fun putFiles(sftpSession: Session, fileNames: List<String>, directory: Directories = Directories.INBOUND) =
        (sftpSession.openChannel("sftp") as ChannelSftp).apply {
            connect()
            fileNames.forEach { fileName ->
                put(
                    fileAsString("/FtpFiler/$fileName").toByteArray().inputStream(),
                    "${directory.value}/$fileName"
                )
            }
        }
}
