package sokos.ske.krav.avstemming.kontrakt

import java.math.BigDecimal
import javax.xml.bind.annotation.XmlAccessType
import javax.xml.bind.annotation.XmlAccessorType
import javax.xml.bind.annotation.XmlSchemaType
import javax.xml.bind.annotation.XmlType

/**
 * Grunnlagsrecord (id-130) for å skille mellom antall godkjente og avviste meldinger, antall godkjente med varsel og antall meldinger hvor avleverende system ikke har mottatt kvitteringsmelding.
 *
 *
 * Java class for Grunnlagsdata complex type.
 *
 *
 * The following schema fragment specifies the expected content contained within this class.
 *
 * <pre>
 * &lt;complexType name="Grunnlagsdata">
 * &lt;complexContent>
 * &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 * &lt;sequence>
 * &lt;element name="godkjentAntall" type="{http://www.w3.org/2001/XMLSchema}int"/>
 * &lt;element name="godkjentBelop" type="{http://www.w3.org/2001/XMLSchema}decimal" minOccurs="0"/>
 * &lt;element name="godkjentFortegn" type="{http://nav.no/virksomhet/tjenester/avstemming/meldinger/v1}Fortegn" minOccurs="0"/>
 * &lt;element name="varselAntall" type="{http://www.w3.org/2001/XMLSchema}int"/>
 * &lt;element name="varselBelop" type="{http://www.w3.org/2001/XMLSchema}decimal" minOccurs="0"/>
 * &lt;element name="varselFortegn" type="{http://nav.no/virksomhet/tjenester/avstemming/meldinger/v1}Fortegn" minOccurs="0"/>
 * &lt;element name="avvistAntall" type="{http://www.w3.org/2001/XMLSchema}int"/>
 * &lt;element name="avvistBelop" type="{http://www.w3.org/2001/XMLSchema}decimal" minOccurs="0"/>
 * &lt;element name="avvistFortegn" type="{http://nav.no/virksomhet/tjenester/avstemming/meldinger/v1}Fortegn" minOccurs="0"/>
 * &lt;element name="manglerAntall" type="{http://www.w3.org/2001/XMLSchema}int"/>
 * &lt;element name="manglerBelop" type="{http://www.w3.org/2001/XMLSchema}decimal" minOccurs="0"/>
 * &lt;element name="manglerFortegn" type="{http://nav.no/virksomhet/tjenester/avstemming/meldinger/v1}Fortegn" minOccurs="0"/>
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
  name = "Grunnlagsdata",
  namespace = "http://nav.no/virksomhet/tjenester/avstemming/meldinger/v1",
  propOrder = ["godkjentAntall", "godkjentBelop", "godkjentFortegn", "varselAntall", "varselBelop", "varselFortegn", "avvistAntall", "avvistBelop", "avvistFortegn", "manglerAntall", "manglerBelop", "manglerFortegn"
  ]
)
class Grunnlagsdata {
  /**
   * Gets the value of the godkjentAntall property.
   *
   */
  /**
   * Sets the value of the godkjentAntall property.
   *
   */
  var godkjentAntall: Int = 0
  /**
   * Gets the value of the godkjentBelop property.
   *
   * @return
   * possible object is
   * [BigDecimal]
   */
  /**
   * Sets the value of the godkjentBelop property.
   *
   * @param value
   * allowed object is
   * [BigDecimal]
   */
  var godkjentBelop: BigDecimal? = null
  /**
   * Gets the value of the godkjentFortegn property.
   *
   * @return
   * possible object is
   * [Fortegn]
   */
  /**
   * Sets the value of the godkjentFortegn property.
   *
   * @param value
   * allowed object is
   * [Fortegn]
   */
  @XmlSchemaType(name = "string")
  var godkjentFortegn: Fortegn? = null
  /**
   * Gets the value of the varselAntall property.
   *
   */
  /**
   * Sets the value of the varselAntall property.
   *
   */
  var varselAntall: Int = 0
  /**
   * Gets the value of the varselBelop property.
   *
   * @return
   * possible object is
   * [BigDecimal]
   */
  /**
   * Sets the value of the varselBelop property.
   *
   * @param value
   * allowed object is
   * [BigDecimal]
   */
  var varselBelop: BigDecimal? = null
  /**
   * Gets the value of the varselFortegn property.
   *
   * @return
   * possible object is
   * [Fortegn]
   */
  /**
   * Sets the value of the varselFortegn property.
   *
   * @param value
   * allowed object is
   * [Fortegn]
   */
  @XmlSchemaType(name = "string")
  var varselFortegn: Fortegn? = null
  /**
   * Gets the value of the avvistAntall property.
   *
   */
  /**
   * Sets the value of the avvistAntall property.
   *
   */
  var avvistAntall: Int = 0
  /**
   * Gets the value of the avvistBelop property.
   *
   * @return
   * possible object is
   * [BigDecimal]
   */
  /**
   * Sets the value of the avvistBelop property.
   *
   * @param value
   * allowed object is
   * [BigDecimal]
   */
  var avvistBelop: BigDecimal? = null
  /**
   * Gets the value of the avvistFortegn property.
   *
   * @return
   * possible object is
   * [Fortegn]
   */
  /**
   * Sets the value of the avvistFortegn property.
   *
   * @param value
   * allowed object is
   * [Fortegn]
   */
  @XmlSchemaType(name = "string")
  var avvistFortegn: Fortegn? = null
  /**
   * Gets the value of the manglerAntall property.
   *
   */
  /**
   * Sets the value of the manglerAntall property.
   *
   */
  var manglerAntall: Int = 0
  /**
   * Gets the value of the manglerBelop property.
   *
   * @return
   * possible object is
   * [BigDecimal]
   */
  /**
   * Sets the value of the manglerBelop property.
   *
   * @param value
   * allowed object is
   * [BigDecimal]
   */
  var manglerBelop: BigDecimal? = null
  /**
   * Gets the value of the manglerFortegn property.
   *
   * @return
   * possible object is
   * [Fortegn]
   */
  /**
   * Sets the value of the manglerFortegn property.
   *
   * @param value
   * allowed object is
   * [Fortegn]
   */
  @XmlSchemaType(name = "string")
  var manglerFortegn: Fortegn? = null
}
