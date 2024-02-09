package sokos.ske.krav.avstemming.kontrakt

import javax.xml.bind.annotation.XmlEnum
import javax.xml.bind.annotation.XmlType


@XmlType(name = "KildeType", namespace = "http://nav.no/virksomhet/tjenester/avstemming/meldinger/v1")
@XmlEnum
enum class KildeType {
  
  AVLEV,
  MOTT;

  fun value(): String {
	return name
  }

  companion object {
	fun fromValue(v: String?): KildeType {
	  return valueOf(v!!)
	}
  }
}
