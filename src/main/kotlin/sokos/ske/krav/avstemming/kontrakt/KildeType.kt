package sokos.ske.krav.avstemming.kontrakt

import javax.xml.bind.annotation.XmlEnum
import javax.xml.bind.annotation.XmlType

/**
 *
 * Java class for KildeType.
 *
 *
 * The following schema fragment specifies the expected content contained within this class.
 *
 *
 * <pre>
 * &lt;simpleType name="KildeType">
 * &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
 * &lt;enumeration value="AVLEV"/>
 * &lt;enumeration value="MOTT"/>
 * &lt;/restriction>
 * &lt;/simpleType>
</pre> *
 *
 */
@XmlType(name = "KildeType", namespace = "http://nav.no/virksomhet/tjenester/avstemming/meldinger/v1")
@XmlEnum
enum class KildeType {
  /**
   * Avleverende komponent
   *
   */
  AVLEV,

  /**
   * Mottakende komponent
   *
   */
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
