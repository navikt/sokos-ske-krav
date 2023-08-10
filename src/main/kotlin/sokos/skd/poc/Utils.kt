package sokos.skd.poc

import java.io.File
import java.net.URL


fun readFileFromFtp(fileName: String): List<String> {
    val ftpClient = FtpClient()
    val lines = ftpClient.downloadFileFromFtp(fileName)   // File(fileName).readLines()
    return lines
}
fun readFileFromFS(file: URL): List<String> {
    val lines = File(file.toURI()).readLines()
    return lines
}


