package sokos.skd.poc

import java.io.File
import java.net.URL


fun readFileFromFtp(fileName: String): List<String> {
    val ftpClient = FtpClient()
    return ftpClient.downloadFileFromFtp(fileName)
}
fun readFileFromFS(file: URL): List<String> {
    return File(file.toURI()).readLines()
}


