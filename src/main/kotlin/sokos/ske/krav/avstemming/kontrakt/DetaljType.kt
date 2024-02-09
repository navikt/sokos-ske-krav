package sokos.ske.krav.avstemming.kontrakt

import javax.xml.bind.annotation.XmlEnum
import javax.xml.bind.annotation.XmlType


@XmlType(name = "detaljType", namespace = "http://nav.no/virksomhet/tjenester/avstemming/meldinger/v1")
@XmlEnum
enum class DetaljType {

  VARS,
  AVVI,
  MANG;

  fun value(): String {
	return name
  }

  companion object {
	fun fromValue(v: String?): DetaljType {
	  return valueOf(v!!)
	}
  }
}
