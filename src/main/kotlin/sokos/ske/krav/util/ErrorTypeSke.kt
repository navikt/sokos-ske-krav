package sokos.ske.krav.util


enum class ErrorTypeSke(val value: String) {
    KRAV_IKKE_RESKONTROFORT_RESEND("innkrevingsoppdrag-er-ikke-reskontrofoert"),
    KRAV_ER_AVSKREVET("innkrevingsoppdrag-er-avskrevet"),
    KRAV_ER_ALLEREDE_AVSKREVET("innkrevingsoppdrag-er-allerede-avskrevet");

    companion object {
        private val map = ErrorTypeSke.entries.associateBy { it.value }
        infix fun from(value: String) = map[value]

    }

}