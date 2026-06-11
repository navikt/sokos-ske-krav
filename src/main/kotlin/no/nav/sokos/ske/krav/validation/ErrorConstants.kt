package no.nav.sokos.ske.krav.validation

enum class ErrorKeys(
    val value: String,
) {
    // File validatation error keys
    PARSE_EXCEPTION("Exception i parsing av fil"),
    FEIL_I_ANTALL("Antall krav stemmer ikke med antallet i siste linje"),
    FEIL_I_SUM("Sum alle linjer stemmer ikke med sum i siste linje"),
    FEIL_I_DATO("Dato sendt er avvikende mellom første og siste linje fra OS"),
    FAGSYSTEMID_MANGLER("fagsystemId mangler i en eller flere kravlinjer"),

    // Line validation error keys
    VEDTAKSDATO_ERROR("Feil med vedtaksdato"),
    UTBETALINGSDATO_ERROR("Feil med utbetalingsdato"),
    PERIODE_ERROR("Feil med periode"),
    SAKSNUMMER_ERROR("Feil med saksnummer"),
    REFERANSENUMMERGAMMELSAK_ERROR("Feil med ReferanseNummerGammelSak"),
    REFERANSENUMMERGAMMELSAK_MISSING("Manglende ReferanseNummerGammelSak"),
    KRAVTYPE_ERROR("Kravtype finnes ikke definert for oversending til skatt"),
    TILLEGGSFRISTDATO_ERROR("Feil med tilleggsfristdato"),
    GJELDERID_ERROR("Feil med gjelderId"),
    FAGSYSTEMID_ERROR("Feil med fagsystemId"),
    HOVEDSTOL_ERROR("Feil med hovedstol"),
}

enum class ErrorMessages(
    val description: String,
)
