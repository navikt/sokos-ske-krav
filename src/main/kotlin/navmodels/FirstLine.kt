package navmodels

import java.time.LocalDateTime

class FirstLine(
    val transferDate: LocalDateTime,
    val sender: String
)
{
    override fun toString(): String {
        return "FirstLine(transferDate=$transferDate, sender='$sender')"
    }
}

