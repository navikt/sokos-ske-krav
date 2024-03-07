package sokos.ske.krav.database.models

enum class Status(val value: String) {
    KRAV_IKKE_SENDT("KRAV_IKKE_SENDT"),
    KRAV_SENDT("KRAV_SENDT"),
    MOTTATT_UNDERBEHANDLING("MOTTATT_UNDER_BEHANDLING"),
    RESKONTROFOERT("RESKONTROFOERT"),

    UGYLDIG_FORESPORSEL_400("400_Ugyldig_forespørsel"),
    FEIL_AUTENTISERING_401("401_KLIENTEN_ER_IKKE_AUTENTISERT"),
    INGEN_TILGANG_403("403_KLIENTEN_ER_IKKE_AUTORISERT"),


    FANT_IKKE_SAKSREF_404("404_SKE_FANT_IKKE_SAKSREF"),
    ANNEN_IKKE_FUNNET_404("404_ANNEN_IKKE_FUNNET"),

    FEIL_MEDIETYPE_406("406_FEIL_MEDIETYPE_I_REQUEST"),

    KRAV_ER_AVSKREVET_409("409_KRAV_ER AVSKREVET"),
    IKKE_RESKONTROFORT_RESEND("409_KRAV_ER_IKKE_RESKONTROFØRT_RESEND"),
    IKKE_RESKONTROFORT_409("409_KRAV_ER_IKKE_RESKONTROFØRT"),
    ANNEN_KONFLIKT_409("409_ANNEN_KONFLIKT"),

    VALIDERINGSFEIL_MOTTAKSSTATUS("VALIDERINGSFEIL"),
    VALIDERINGSFEIL_422("422_VALIDERINGSFEIL"),
    ANNEN_VALIDERINGSFEIL_422("422_ANNEN VALIDERINGSFEIL"),

    INTERN_TJENERFEIL_500("500_FEIL_PÅ_MOTTAKSERVER"),
    UTILGJENGELIG_TJENESTE_503("503_TJENESTEN_ER_IKKE_TILGJENGELIG"),

    UKJENT_FEIL("UKJENT_FEIL"),
    UKJENT_STATUS("UKJENT_STATUS");

    companion object {
        private val map = Status.entries.associateBy { it.value }
        infix fun from(value: String) = map[value]

        fun Status.isOkStatus(): Boolean {
            return when (this) {
                KRAV_IKKE_SENDT,
                KRAV_SENDT,
                MOTTATT_UNDERBEHANDLING,
                RESKONTROFOERT,
                IKKE_RESKONTROFORT_RESEND -> true
                else -> false
            }
        }

        fun Status.isResendStatus(): Boolean {
            return when (this) {
                KRAV_IKKE_SENDT,
                IKKE_RESKONTROFORT_RESEND,
                INTERN_TJENERFEIL_500,
                UTILGJENGELIG_TJENESTE_503 -> true
                else -> false
            }
        }

        fun Status.isAlarm(): Boolean {
            return when (this) {
                UGYLDIG_FORESPORSEL_400,
                FEIL_AUTENTISERING_401,
                INGEN_TILGANG_403,
                FANT_IKKE_SAKSREF_404,
                ANNEN_IKKE_FUNNET_404,
                FEIL_MEDIETYPE_406,
                KRAV_ER_AVSKREVET_409,
                IKKE_RESKONTROFORT_409,
                ANNEN_KONFLIKT_409,
                VALIDERINGSFEIL_422,
                ANNEN_VALIDERINGSFEIL_422,
                UKJENT_FEIL,    
                UKJENT_STATUS -> true
                else -> false
            }
        }
    }
}