package sokos.skd.poc

import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import java.io.ByteArrayOutputStream
import java.util.*

class FtpClient {
    val props = Properties().also { it.load(this::class.java.classLoader.getResourceAsStream("ftpprops.local"))  }
    val ftpServer= props.getProperty("ftpServer")
    var ftpUsername = props.getProperty("ftpUsername")
    var ftpPassword = props.getProperty("ftpPassword")
    var ftpDirectory = props.getProperty("ftpDirectory")
}
fun FtpClient.downloadFileFromFtp(fileName: String): List<String> {

    val ftpClient = FTPClient()
    val outputStream = ByteArrayOutputStream()

    ftpClient.connect(ftpServer)
    ftpClient.login(ftpUsername, ftpPassword)
    ftpClient.enterLocalPassiveMode()
    ftpClient.setFileType(FTP.LOCAL_FILE_TYPE)
    ftpClient.changeWorkingDirectory(ftpDirectory)

    ftpClient.retrieveFile(fileName, outputStream)

    val result = String(outputStream.toByteArray())

    outputStream.close()
    ftpClient.logout()
    ftpClient.disconnect()

    return result.split("\n").filter { it.length > 0 }
}