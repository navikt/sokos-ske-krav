package no.nav.sokos.ske.krav.validation

enum class ErrorKeys(
    val value: String,
) {
    // File validation error keys
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
) {
    VEDTAKSDATO_WRONG_FORMAT("Vedtaksdato er feil formattert i fil"),
    VEDTAKSDATO_IS_IN_FUTURE("Vedtaksdato kan ikke være i fremtiden"),
    UTBETALINGSDATO_WRONG_FORMAT("Utbetalingsdato er feil formattert i fil"),
    UTBETALINGSDATO_IS_NOT_BEFORE_VEDTAKSDATO("Utbetalingsdato må være tidligere enn vedtaksdato"),
    PERIODE_FOM_WRONG_FORMAT("FOM er feil formattert i fil"),
    PERIODE_TOM_WRONG_FORMAT("TOM er feil formattert i fil"),
    PERIODE_FOM_IS_AFTER_PERIODE_TOM("Periode FOM kan ikke være etter TOM"),
    PERIODE_TOM_IS_IN_INVALID_FUTURE("Periode TOM kan ikke være etter inneværende måned"),
    UNKNOWN_DATE_ERROR("Ukjent datofeil"),
    SAKSNUMMER_WRONG_FORMAT("Saksnummer er feil formattert i fil"),
    REFERANSENUMMERGAMMELSAK_WRONG_FORMAT("ReferanseNummerGammelSak er feil formattert i fil"),
    REFERANSENUMMERGAMMELSAK_MANGLER_FOR_STOPP("ReferanseNummerGammelSak mangler for stopp i fil"),
    KRAVTYPE_DOES_NOT_EXIST("Kravtype finnes ikke definert for oversending til skatt"),
    TILLEGGSFRISTDATO_TOO_OLD("Tilleggsfristdato kan ikke være lengre tilbake i tid enn 10 måneder fra dagens dato"),
    TILLEGGSFRISTDATO_WRONG_FORMAT("Tilleggsfristdato er feil formattert i fil"),
    GJELDERID_MISSING("gjelderId mangler"),
    FAGSYSTEMID_MISSING("fagsystemId mangler"),
    BELOP_NEGATIVE("Beløp kan ikke være negativt"),
    ;

    override fun toString() = description
}
