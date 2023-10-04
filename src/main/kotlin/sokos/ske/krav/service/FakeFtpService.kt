package sokos.ske.krav.service

import mu.KotlinLogging
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.mockftpserver.fake.FakeFtpServer
import org.mockftpserver.fake.UserAccount
import org.mockftpserver.fake.filesystem.DirectoryEntry
import org.mockftpserver.fake.filesystem.FileEntry
import org.mockftpserver.fake.filesystem.UnixFakeFileSystem

import sokos.ske.krav.config.PropertiesConfig
import sokos.ske.krav.navmodels.DetailLine

import java.io.ByteArrayOutputStream
import java.io.File


enum class Directories(val value: String){
    OUTBOUND("${File.separator}ut"),
    SENDT("${File.separator}behandlet"),
    FAILED("${File.separator}feilfiler")
}

data class FtpFil(
    val name: String,
    val content: List<String>,
    val detailLines: List<DetailLine>
)
class FakeFtpService(private val client: FTPClient = FTPClient()) {
 //   private val config = PropertiesConfig.FtpConfig()
    private val fakeFtpServer = FakeFtpServer()
    private val logger = KotlinLogging.logger {}

    fun connect(directory: Directories = Directories.OUTBOUND, fileNames: List<String> = listOf("fil1.txt", "fil2.txt")): FTPClient {
        println("FTP Connecting")
        fakeFtpServer.serverControlPort = 0
        fakeFtpServer.addUserAccount(UserAccount("username", "password", "/"))
        fakeFtpServer.fileSystem = UnixFakeFileSystem().apply {
            add(DirectoryEntry(directory.value))
            fileNames.forEach{fileName ->
                val path = "${directory.value}${File.separator}$fileName"
                add(FileEntry(path, fileName.asText()))
            }
        }
        fakeFtpServer.start()
        while(!fakeFtpServer.isStarted) { Thread.sleep(100)}
       // client.init(PropertiesConfig.FtpConfig(port = fakeFtpServer.serverControlPort))

        client.connect("localhost", fakeFtpServer.serverControlPort)
        client.login("username","password",)
        client.enterLocalPassiveMode()
        client.setFileType(FTP.LOCAL_FILE_TYPE)
        client.changeWorkingDirectory("/")
        return client
    }

    fun close() = fakeFtpServer.stop()
    fun createFile(fileName: String, content: List<String>, directory:Directories) {
        val path = "${directory.value}${File.separator}$fileName"
        fakeFtpServer.fileSystem.add(FileEntry(path, content.joinToString("\n")))
        File(fileName).writeText(content.joinToString("\n")) //for testing
    }
    fun moveFile(name: String, from: Directories, to: Directories) {
        fakeFtpServer.fileSystem.rename("${from.value}${File.separator}${name}", "${to.value}${File.separator}${name}")
        listFiles(to)
    }

    fun listFiles(directory: Directories = Directories.OUTBOUND): List<String> {
        val files =  client.listFiles(directory.value)
        println("Files in directory $directory : ${files.map { it.name }}")
        return files.map { it.name }
    }

    fun getFiles(validator: (content: List<String>) -> ValidationResult, directory: Directories = Directories.OUTBOUND): MutableList<FtpFil> {
        val successFiles = mutableListOf<FtpFil>()
        downloadNewFiles(directory).map{entry ->
            when(val result: ValidationResult = validator(entry.value)){
                is ValidationResult.Success -> successFiles.add(FtpFil(entry.key, entry.value, result.detailLines))
                is ValidationResult.Error -> {
                    println("validering for ${entry.key}")
                    moveFile(entry.key, Directories.OUTBOUND, Directories.FAILED)
                    logger.info { result.message }
                    println(result.message)
                }
            }
        }
        return successFiles
    }

    private fun downloadNewFiles(directory: Directories = Directories.OUTBOUND): Map<String, List<String>> =  listFiles(directory).associateWith { client.downloadFile("${directory.value}${File.separator}$it") }
}


fun FTPClient.downloadFile(fileName: String): List<String> {
    val outputStream = ByteArrayOutputStream()
    retrieveFile(fileName, outputStream)

    return String(outputStream.toByteArray()).split("\n").filter { it.isNotEmpty() }
}
fun FTPClient.init(config: PropertiesConfig.FtpConfig = PropertiesConfig.FtpConfig()){
    connect(config.server, config.port)
    login(config.username, config.password)
    enterLocalPassiveMode()
    setFileType(FTP.LOCAL_FILE_TYPE)
    changeWorkingDirectory(config.homeDirectory)
}

fun FTPClient.close(){

}
fun String.asText(): String =  object {}.javaClass.classLoader.getResourceAsStream(this)!!.bufferedReader().use { it.readText() }