import org.junit.jupiter.api.Test

class FtpClientKtTest {


    @Test
    fun t1() {
        val ftpClient = FtpClient()
        ftpClient.downloadFileFromFtp("eksempelfil_TBK.txt").let { it.forEach { println(it) } }
    }
}