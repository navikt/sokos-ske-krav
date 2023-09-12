package sokos.ske.krav.navmodels

import kotlinx.serialization.Serializable


@Serializable
data class FirstLine(
    val transferDate: kotlinx.datetime.LocalDateTime,
    val sender: String
)
{
    override fun toString(): String {
        return "FirstLine(transferDate=$transferDate, sender='$sender')"
    }
}

