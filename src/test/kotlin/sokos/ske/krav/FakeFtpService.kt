package sokos.ske.krav


import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import mu.KotlinLogging
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.mockftpserver.fake.FakeFtpServer
import org.mockftpserver.fake.UserAccount
import org.mockftpserver.fake.filesystem.DirectoryEntry
import org.mockftpserver.fake.filesystem.FileEntry
import org.mockftpserver.fake.filesystem.UnixFakeFileSystem
import sokos.ske.krav.config.PropertiesConfig
import sokos.ske.krav.service.Directories
import sokos.ske.krav.service.FtpService
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream


class FakeFtpService(private val client: FTPClient = FTPClient()) {
    private val fakeFtpServer = FakeFtpServer()
    private val logger = KotlinLogging.logger {}

    fun setupMocks(
        directory: Directories = Directories.INBOUND,
        fileNames: List<String> = listOf("fil1.txt", "fil2.txt")
    ): FtpService {

        val config = connect(directory, fileNames)
        val jsch = mockk<JSch>(relaxUnitFun = true, relaxed = true) {
            every { getSession(any<String>(), any<String>(), any<Int>()) } returns mockk<Session>(relaxed = true) {
                every { openChannel("sftp") } returns mockk<ChannelSftp>(relaxed=true) {
                    every { get(any<String>(), any<ByteArrayOutputStream>()) } answers { client.retrieveFile(firstArg<String>(), secondArg<ByteArrayOutputStream>()) }
                    every { put(any<InputStream>(), any<String>()) } answers { fakeFtpServer.fileSystem.add(FileEntry(secondArg<String>(), firstArg<InputStream>().toString())) }
                    every { rename(any<String>(), any<String>()) } answers { fakeFtpServer.fileSystem.rename(firstArg<String>(), secondArg<String>()) }
                }

            }
        }

        val ftpService = spyk(FtpService(config, jsch = jsch)) {
            every { listFiles(any<Directories>()) } answers{ client.listFiles(firstArg<Directories>().value).filter { it.name.contains(".") }.map { it.name }}
        }
        return ftpService

    }

    fun connect(
        directory: Directories = Directories.INBOUND,
        fileNames: List<String> = listOf("fil1.txt", "fil2.txt")
    ): PropertiesConfig.FtpConfig {
        fakeFtpServer.serverControlPort = 0
        fakeFtpServer.addUserAccount(UserAccount("username", "password", "/"))
        fakeFtpServer.fileSystem = UnixFakeFileSystem().apply {
            add(DirectoryEntry(Directories.INBOUND.value))
            add(DirectoryEntry(Directories.FAILED.value))
            add(DirectoryEntry(Directories.OUTBOUND.value))
            fileNames.forEach { fileName ->
                val path = "${directory.value}${File.separator}$fileName"
                add(FileEntry(path, fileName.asText()))
            }
        }
        fakeFtpServer.start()
        while (!fakeFtpServer.isStarted) {
            Thread.sleep(100)
        }

        client.connect("localhost", fakeFtpServer.serverControlPort)
        client.login("username", "password")
        client.enterLocalPassiveMode()
        client.setFileType(FTP.LOCAL_FILE_TYPE)
        client.changeWorkingDirectory("/")

        return PropertiesConfig.FtpConfig(
            server = "localhost",
            username = "username",
            keyPass = "test",
            port = fakeFtpServer.serverControlPort,
            privKey = "src/test/resources/fakePrivateKey",
            hostKey = "",
        )
    }

    fun close() = fakeFtpServer.stop()

}


fun String.asText(): String =  object {}.javaClass.classLoader.getResourceAsStream(this)!!.bufferedReader().use { it.readText() }