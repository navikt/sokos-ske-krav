package sokos.skd.poc.navmodels

import java.time.LocalDateTime

data class LastLine(
    val transferDate: LocalDateTime,
    val sender: String,
    val numTransactionLines: Int,
    val sumAllTransactionLines: Double,
)
{
    override fun toString(): String {
        return "LastLine(transferDate=$transferDate, sender='$sender', numTransactionLines=$numTransactionLines, sumAllTransactionLines=$sumAllTransactionLines)"
    }

}
