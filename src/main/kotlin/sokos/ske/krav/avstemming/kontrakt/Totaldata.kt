package sokos.ske.krav.avstemming.kontrakt

import java.math.BigDecimal
import javax.xml.bind.annotation.XmlAccessType
import javax.xml.bind.annotation.XmlAccessorType
import javax.xml.bind.annotation.XmlSchemaType
import javax.xml.bind.annotation.XmlType

/**
 * Grensesnittavstemmingen skal minimum bestå av en id-110 (aksjonskode ’DATA) og en totalrecord (id-120) i tillegg til START- og SLUTT-recorden.
 *
 *
 * Java class for Totaldata complex type.
 *
 *
 * The following schema fragment specifies the expected content contained within this class.
 *
 * <pre>
 * &lt;complexType name="Totaldata">
 * &lt;complexContent>
 * &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 * &lt;sequence>
 * &lt;element name="totalAntall" type="{http://www.w3.org/2001/XMLSchema}int"/>
 * &lt;element name="totalBelop" type="{http://www.w3.org/2001/XMLSchema}decimal" minOccurs="0"/>
 * &lt;element name="fortegn" type="{http://nav.no/virksomhet/tjenester/avstemming/meldinger/v1}Fortegn" minOccurs="0"/>
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
  name = "Totaldata", namespace = "http://nav.no/virksomhet/tjenester/avstemming/meldinger/v1", propOrder = ["totalAntall", "totalBelop", "fortegn"
  ]
)
class Totaldata {
  /**
   * Gets the value of the totalAntall property.
   *
   */
  /**
   * Sets the value of the totalAntall property.
   *
   */
  var totalAntall: Int = 0
  /**
   * Gets the value of the totalBelop property.
   *
   * @return
   * possible object is
   * [BigDecimal]
   */
  /**
   * Sets the value of the totalBelop property.
   *
   * @param value
   * allowed object is
   * [BigDecimal]
   */
  var totalBelop: BigDecimal? = null
  /**
   * Gets the value of the fortegn property.
   *
   * @return
   * possible object is
   * [Fortegn]
   */
  /**
   * Sets the value of the fortegn property.
   *
   * @param value
   * allowed object is
   * [Fortegn]
   */
  @XmlSchemaType(name = "string")
  var fortegn: Fortegn? = null
}
