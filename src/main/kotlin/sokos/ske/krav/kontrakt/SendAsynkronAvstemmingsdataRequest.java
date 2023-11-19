
package sokos.ske.krav.kontrakt;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for SendAsynkronAvstemmingsdataRequest complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="SendAsynkronAvstemmingsdataRequest">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="avstemmingsdata" type="{http://nav.no/virksomhet/tjenester/avstemming/meldinger/v1}Avstemmingsdata"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "SendAsynkronAvstemmingsdataRequest", namespace = "http://nav.no/virksomhet/tjenester/avstemming/meldinger/v1", propOrder = {
    "avstemmingsdata"
})
public class SendAsynkronAvstemmingsdataRequest {

    @XmlElement(required = true)
    protected Avstemmingsdata avstemmingsdata;

    /**
     * Gets the value of the avstemmingsdata property.
     * 
     * @return
     *     possible object is
     *     {@link Avstemmingsdata }
     *     
     */
    public Avstemmingsdata getAvstemmingsdata() {
        return avstemmingsdata;
    }

    /**
     * Sets the value of the avstemmingsdata property.
     * 
     * @param value
     *     allowed object is
     *     {@link Avstemmingsdata }
     *     
     */
    public void setAvstemmingsdata(Avstemmingsdata value) {
        this.avstemmingsdata = value;
    }

}
