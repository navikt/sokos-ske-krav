package sokos.ske.krav.avstemming.kontrakt

import javax.xml.bind.annotation.XmlEnum
import javax.xml.bind.annotation.XmlType

/**
 *
 * Java class for AvstemmingType.
 *
 *
 * The following schema fragment specifies the expected content contained within this class.
 *
 *
 * <pre>
 * &lt;simpleType name="AvstemmingType">
 * &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
 * &lt;enumeration value="GRSN"/>
 * &lt;enumeration value="KONS"/>
 * &lt;enumeration value="PERI"/>
 * &lt;/restriction>
 * &lt;/simpleType>
</pre> *
 *
 */
@XmlType(name = "AvstemmingType", namespace = "http://nav.no/virksomhet/tjenester/avstemming/meldinger/v1")
@XmlEnum
enum class AvstemmingType {
  /**
   * Grensesnittavstemming
   *
   */
  GRSN,

  /**
   * Konsistensavstemming
   *
   */
  KONS,

  /**
   * Periodeavstemming
   *
   */
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
