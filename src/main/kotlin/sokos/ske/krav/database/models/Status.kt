package sokos.ske.krav.database.models

enum class Status(val value: String) {
    KRAV_IKKE_SENDT("KRAV_IKKE_SENDT"),
    KRAV_SENDT("KRAV_SENDT"),
    MOTTATT_UNDERBEHANDLING("MOTTATT_UNDER_BEHANDLING"),
    RESKONTROFOERT("RESKONTROFOERT"),

    FANT_IKKE_SAKSREF("404_SKE_FANT_IKKE_SAKSREF"),
    ANNEN_IKKE_FUNNET("404_ANNEN_IKKE_FUNNET"),

    ER_AVSKREVET("409_KRAV_ER AVSKREVET"),
    IKKE_RESKONTROFORT_RESEND("409_KRAV_ER_IKKE_RESKONTROFØRT_RESEND"),
    IKKE_RESKONTROFORT("409_KRAV_ER_IKKE_RESKONTROFØRT"),
    ANNEN_KONFLIKT("409_ANNEN_KONFLIKT"),

    VALIDERINGSFEIL("422_VALIDERINGSFEIL"),
    ANNEN_VALIDERINGSFEIL("422_ANNEN VALIDERINGSFEIL"),

    UKJENT_FEIL("UKJENT_FEIL"),
    UKJENT_STATUS("UKJENT_STATUS");

    companion object {
        private val map = Status.entries.associateBy { it.value }
        infix fun from(value: String) = map[value]

        infix fun isOkStatus(status: Status): Boolean {
            return when (status) {
                KRAV_IKKE_SENDT -> true
                KRAV_SENDT -> true
                MOTTATT_UNDERBEHANDLING -> true
                RESKONTROFOERT -> true
                IKKE_RESKONTROFORT_RESEND -> true
                else -> false
            }
        }
    }
}