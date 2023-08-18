package sokos.skd.poc


import org.junit.jupiter.api.Test
import kotlin.test.Ignore

class FtpClientKtTest {


    @Ignore
    @Test
    fun t1() {
        val ftpClient = FtpClient()
        ftpClient.downloadFileFromFtp("eksempelfil_TBK.txt").let { it.forEach { println(it) } }
    }
}