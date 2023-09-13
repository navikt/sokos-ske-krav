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
import java.net.URL

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
class FtpService(private val client: FTPClient = FTPClient()) {
    private val config = PropertiesConfig.FtpConfig()
    private val fakeFtpServer = FakeFtpServer()
    private val log = KotlinLogging.logger {}

    fun connect(directory: Directories = Directories.OUTBOUND, fileNames: List<String> = listOf("fil1.txt", "fil2.txt")): FTPClient {
        fakeFtpServer.serverControlPort = config.port
        fakeFtpServer.addUserAccount(UserAccount(config.username, config.password, config.homeDirectory))

        fakeFtpServer.fileSystem = UnixFakeFileSystem().apply {
            add(DirectoryEntry(directory.value))
            fileNames.forEach{fileName ->
                val filecontent = try{File(fileName.asUrl().toURI()).readText()
                    }catch (e: Exception ){File(fileName.asExternal()).readText()}
                val path = "${directory.value}${File.separator}$fileName"
                add(FileEntry(path, filecontent))
            }
        }
        fakeFtpServer.start()
        while(!fakeFtpServer.isStarted) { Thread.sleep(100)}
        client.init(PropertiesConfig.FtpConfig(port = fakeFtpServer.serverControlPort))
        return client
    }
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
                    log.info { result.message }
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

fun String.asUrl(): URL = object {}.javaClass.classLoader.getResource(this)!!
fun String.asExternal(): String = object {}.javaClass.classLoader.getResource(this)!!.toExternalForm()