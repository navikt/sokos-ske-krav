package sokos.skd.poc.service

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import mu.KotlinLogging
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.mockftpserver.fake.FakeFtpServer
import org.mockftpserver.fake.UserAccount
import org.mockftpserver.fake.filesystem.DirectoryEntry
import org.mockftpserver.fake.filesystem.FileEntry
import org.mockftpserver.fake.filesystem.UnixFakeFileSystem
import sokos.skd.poc.config.PropertiesConfig
import sokos.skd.poc.navmodels.FtpFil
import sokos.skd.poc.skdmodels.NyttOppdrag.OpprettInnkrevingsoppdragRequest
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URL

enum class Directories(val value: String){
    OUTBOUND("/ut"),
    SENDT("/behandlet"),
    FAILED("/feilfiler")
}
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
                add(FileEntry("${directory.value}/$fileName", File(fileName.asUrl().toURI()).readText()))
            }
        }
        fakeFtpServer.start()
        while(!fakeFtpServer.isStarted) { Thread.sleep(100)}
        client.init(PropertiesConfig.FtpConfig(port = fakeFtpServer.serverControlPort))
        return client
    }

    fun downloadNewFiles(directory: Directories = Directories.OUTBOUND): List<FtpFil> {
        val newFiles = listFiles(directory)
        val ftpFiles = newFiles.map { name ->
            val file = client.downloadFile("${directory.value}/$name")
            val request = mapFile(file, name, directory)
            val requests: List<JsonElement> = request.map {  it.toJson() }
            FtpFil(name, client.downloadFile("${directory.value}/$name"), request.toJson(), requests)
        }
        return ftpFiles
    }

    fun moveFiles(files:  MutableList<FtpFil>, from: Directories, to: Directories){
        listFiles(from)
        println("number of files to move: ${files.size}")
        files.forEach{ fil ->
            moveFile(fil.name, from, to)
        }
    }

    fun listFiles(directory: Directories = Directories.OUTBOUND): List<String> {
        val files =  client.listFiles(directory.value)
        println("Files in directory $directory : ${files.map { it.name }}")
        return files.map { it.name }
    }

    fun downloadFtpFile(name: String, directory: Directories): FtpFil {
        val request:List<OpprettInnkrevingsoppdragRequest> = downloadFile(name, directory)

        val requests: List<JsonElement> = request.map { it.toJson()}
        println("request size: ${requests.size}")
        return  FtpFil(name, client.downloadFile("${directory.value}/$name"), request.toJson(), requests)

    }
    private fun moveFile(name: String, from: Directories, to: Directories) {
        println("Moving $name to $to")
        fakeFtpServer.fileSystem.rename("${from.value}/${name}", "${to.value}/${name}")
        listFiles(from)
        listFiles(to)
    }



    private fun downloadFile(name: String, directory: Directories= Directories.OUTBOUND,): List<OpprettInnkrevingsoppdragRequest> {
        val file = client.downloadFile("${directory.value}/$name")
        return  mapFile(file, name, directory)
    }


    private fun mapFile(file: List<String>, name: String, directory: Directories= Directories.OUTBOUND,): List<OpprettInnkrevingsoppdragRequest> {
        return try {
            mapFraNavTilSkd(file)
        }catch (e: Exception){
            log.info(e.message)
            println(e.message)
            moveFile(name, directory, Directories.FAILED)
          //  moveFiles(mutableMapOf(name to file.toJson()), directory, Directories.FAILED)
            listOf()
        }

    }

}

private fun mapFraNavTilSkd(liste:List<String>)= listOf<OpprettInnkrevingsoppdragRequest>()

private inline fun <reified OpprettInnkrevingsoppdragRequest> List<OpprettInnkrevingsoppdragRequest>.toJson(): JsonElement = Json.encodeToJsonElement(this)
private inline fun <reified OpprettInnkrevingsoppdragRequest> OpprettInnkrevingsoppdragRequest.toJson(): JsonElement = Json.encodeToJsonElement(this)


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

fun FTPClient.stop(){
    logout()
    disconnect()
}
fun String.asUrl(): URL = object {}.javaClass.classLoader.getResource(this)!!