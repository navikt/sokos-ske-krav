package sokos.ske.krav.avstemming.kontrakt

import javax.xml.bind.annotation.XmlEnum
import javax.xml.bind.annotation.XmlType


@XmlType(name = "Fortegn", namespace = "http://nav.no/virksomhet/tjenester/avstemming/meldinger/v1")
@XmlEnum
enum class Fortegn {
  T,
  F;

  fun value(): String {
	return name
  }

  companion object {
	fun fromValue(v: String?): Fortegn {
	  return valueOf(v!!)
	}
  }
}
