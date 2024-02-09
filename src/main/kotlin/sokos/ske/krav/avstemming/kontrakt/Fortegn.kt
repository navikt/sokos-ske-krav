package sokos.ske.krav.avstemming.kontrakt

import javax.xml.bind.annotation.XmlEnum
import javax.xml.bind.annotation.XmlType

/**
 *
 * Java class for Fortegn.
 *
 *
 * The following schema fragment specifies the expected content contained within this class.
 *
 *
 * <pre>
 * &lt;simpleType name="Fortegn">
 * &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
 * &lt;enumeration value="T"/>
 * &lt;enumeration value="F"/>
 * &lt;/restriction>
 * &lt;/simpleType>
</pre> *
 *
 */
@XmlType(name = "Fortegn", namespace = "http://nav.no/virksomhet/tjenester/avstemming/meldinger/v1")
@XmlEnum
enum class Fortegn {
  /**
   * Tillegg
   *
   */
  T,

  /**
   * Fradrag
   *
   */
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
