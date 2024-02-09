package sokos.ske.krav.avstemming.kontrakt

import javax.xml.bind.annotation.XmlEnum
import javax.xml.bind.annotation.XmlType


@XmlType(name = "AvstemmingType", namespace = "http://nav.no/virksomhet/tjenester/avstemming/meldinger/v1")
@XmlEnum
enum class AvstemmingType {

  GRSN,
  KONS,
  PERI;

  fun value(): String {
	return name
  }

  companion object {
	fun fromValue(v: String?): AvstemmingType {
	  return valueOf(v!!)
	}
  }
}
