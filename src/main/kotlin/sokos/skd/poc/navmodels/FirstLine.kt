package sokos.skd.poc.navmodels

import java.time.LocalDateTime

data class FirstLine(
    val transferDate: LocalDateTime,
    val sender: String
)
{
    override fun toString(): String {
        return "FirstLine(transferDate=$transferDate, sender='$sender')"
    }
}

