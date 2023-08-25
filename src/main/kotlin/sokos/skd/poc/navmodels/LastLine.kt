package sokos.skd.poc.navmodels

import kotlinx.serialization.Serializable

@Serializable
data class LastLine(
    val transferDate: kotlinx.datetime.LocalDateTime,
    val sender: String,
    val numTransactionLines: Int,
    val sumAllTransactionLines: Double,
)
{
    override fun toString(): String {
        return "LastLine(transferDate=$transferDate, sender='$sender', numTransactionLines=$numTransactionLines, sumAllTransactionLines=$sumAllTransactionLines)"
    }

}
