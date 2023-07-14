package navmodels

import java.time.LocalDateTime

class LastLine(
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
