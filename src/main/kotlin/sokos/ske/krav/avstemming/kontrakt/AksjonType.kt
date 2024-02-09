package sokos.ske.krav.avstemming.kontrakt

import javax.xml.bind.annotation.XmlEnum
import javax.xml.bind.annotation.XmlType

@XmlType(name = "AksjonType", namespace = "http://nav.no/virksomhet/tjenester/avstemming/meldinger/v1")
@XmlEnum
enum class AksjonType {
  START,
  DATA,
  AVSL,
  HENT;

  fun value(): String {
	return name
  }

  companion object {
	fun fromValue(v: String?): AksjonType {
	  return valueOf(v!!)
	}
  }
}
