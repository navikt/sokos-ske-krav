package sokos.ske.krav.avstemming.kontrakt

import javax.xml.bind.annotation.XmlAccessType
import javax.xml.bind.annotation.XmlAccessorType
import javax.xml.bind.annotation.XmlElement
import javax.xml.bind.annotation.XmlType

/**
 * Avleverende system må også sende med en perioderecord som definerer for hvilken periode avstemmingen gjelder.
 *
 *
 * Java class for Periodedata complex type.
 *
 *
 * The following schema fragment specifies the expected content contained within this class.
 *
 * <pre>
 * &lt;complexType name="Periodedata">
 * &lt;complexContent>
 * &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 * &lt;sequence>
 * &lt;element name="datoAvstemtFom" type="{http://www.w3.org/2001/XMLSchema}string"/>
 * &lt;element name="datoAvstemtTom" type="{http://www.w3.org/2001/XMLSchema}string"/>
 * &lt;/sequence>
 * &lt;/restriction>
 * &lt;/complexContent>
 * &lt;/complexType>
</pre> *
 *
 *
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(
  name = "Periodedata", namespace = "http://nav.no/virksomhet/tjenester/avstemming/meldinger/v1", propOrder = ["datoAvstemtFom", "datoAvstemtTom"
  ]
)
class Periodedata {
  /**
   * Gets the value of the datoAvstemtFom property.
   *
   * @return
   * possible object is
   * [String]
   */
  /**
   * Sets the value of the datoAvstemtFom property.
   *
   * @param value
   * allowed object is
   * [String]
   */
  @XmlElement(required = true)
  var datoAvstemtFom: String? = null
  /**
   * Gets the value of the datoAvstemtTom property.
   *
   * @return
   * possible object is
   * [String]
   */
  /**
   * Sets the value of the datoAvstemtTom property.
   *
   * @param value
   * allowed object is
   * [String]
   */
  @XmlElement(required = true)
  var datoAvstemtTom: String? = null
}
