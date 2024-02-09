package sokos.ske.krav.avstemming.kontrakt

import javax.xml.bind.annotation.XmlEnum
import javax.xml.bind.annotation.XmlType

/**
 *
 * Java class for DetaljType.
 *
 *
 * The following schema fragment specifies the expected content contained within this class.
 *
 *
 * <pre>
 * &lt;simpleType name="DetaljType">
 * &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
 * &lt;enumeration value="VARS"/>
 * &lt;enumeration value="AVVI"/>
 * &lt;enumeration value="MANG"/>
 * &lt;/restriction>
 * &lt;/simpleType>
</pre> *
 *
 */
@XmlType(name = "detaljType", namespace = "http://nav.no/virksomhet/tjenester/avstemming/meldinger/v1")
@XmlEnum
enum class DetaljType {
  /**
   * Godkjent med varsel
   *
   */
  VARS,

  /**
   * Avvist
   *
   */
  AVVI,

  /**
   * Manglende kvittering
   *
   */
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
