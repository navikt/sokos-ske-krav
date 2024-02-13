package sokos.ske.krav.database.models

enum class Status(val value: String) {
  MOTTATTUNDERBEHANDLING("MOTTATT_UNDER_BEHANDLING"),
  VALIDERINGSFEIL("VALIDERINGSFEIL"),
  RESKONTROFOERT("RESKONTROFOERT"),
  KRAV_SENDT("KRAV_SENDT"),
  KRAV_IKKE_SENDT("KRAV_IKKE_SENDT"),

  FEIL_MED_ENDRING ("FEIL_MED_DEN_ANDRE_ENDRINGEN"),
  FANT_IKKE_SAKSREF ( "SKE_FANT_IKKE_SAKSREF"),
  IKKE_RESKONTROFORT ( "KRAV_ER_IKKE_RESKONTROFØRT");

  companion object {
	private val map = Status.entries.associateBy { it.value }
	infix fun from(value: String) = map[value]
  }
}