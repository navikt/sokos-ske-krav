package sokos.ske.krav.avstemming.kontrakt

import javax.xml.bind.annotation.XmlAccessType
import javax.xml.bind.annotation.XmlAccessorType
import javax.xml.bind.annotation.XmlElement
import javax.xml.bind.annotation.XmlType

/**
 *
 * Java class for SendAsynkronAvstemmingsdataRequest complex type.
 *
 *
 * The following schema fragment specifies the expected content contained within this class.
 *
 * <pre>
 * &lt;complexType name="SendAsynkronAvstemmingsdataRequest">
 * &lt;complexContent>
 * &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 * &lt;sequence>
 * &lt;element name="avstemmingsdata" type="{http://nav.no/virksomhet/tjenester/avstemming/meldinger/v1}Avstemmingsdata"/>
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
  name = "SendAsynkronAvstemmingsdataRequest", namespace = "http://nav.no/virksomhet/tjenester/avstemming/meldinger/v1", propOrder = ["avstemmingsdata"
  ]
)
class SendAsynkronAvstemmingsdataRequest {
  /**
   * Gets the value of the avstemmingsdata property.
   *
   * @return
   * possible object is
   * [Avstemmingsdata]
   */
  /**
   * Sets the value of the avstemmingsdata property.
   *
   * @param value
   * allowed object is
   * [Avstemmingsdata]
   */
  @XmlElement(required = true)
  var avstemmingsdata: Avstemmingsdata? = null
}
