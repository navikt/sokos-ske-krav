
package sokos.ske.krav.avstemming.kontrakt;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import java.util.ArrayList;
import java.util.List;


/**
 * Etter START-recorden skal avstemmingsdataene for konsistensavstemmingen overføres som sammensatte meldinger pr. offnr.
 * 
 *                 Hver slik melding skal starte med id-110 med aksjonskode ’DATA’. Deretter følger de id-kodene som ble brukt da dataene opprinnelig ble overført fra avleverende til mottakende system. For avstemming mot Oppdragssystemet vil dette være deres id-kode 110, 120, 150 osv.
 * 
 * <p>Java class for Avstemmingsdata complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="Avstemmingsdata">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="aksjon" type="{http://nav.no/virksomhet/tjenester/avstemming/meldinger/v1}Aksjonsdata"/>
 *         &lt;element name="total" type="{http://nav.no/virksomhet/tjenester/avstemming/meldinger/v1}Totaldata" minOccurs="0"/>
 *         &lt;element name="periode" type="{http://nav.no/virksomhet/tjenester/avstemming/meldinger/v1}Periodedata" minOccurs="0"/>
 *         &lt;element name="grunnlag" type="{http://nav.no/virksomhet/tjenester/avstemming/meldinger/v1}Grunnlagsdata" minOccurs="0"/>
 *         &lt;element name="detalj" type="{http://nav.no/virksomhet/tjenester/avstemming/meldinger/v1}Detaljdata" maxOccurs="unbounded" minOccurs="0"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "Avstemmingsdata", namespace = "http://nav.no/virksomhet/tjenester/avstemming/meldinger/v1", propOrder = {
    "aksjon",
    "total",
    "periode",
    "grunnlag",
    "detalj"
})
public class Avstemmingsdata {

    @XmlElement(required = true)
    protected Aksjonsdata aksjon;
    protected Totaldata total;
    protected Periodedata periode;
    protected Grunnlagsdata grunnlag;
    protected List<Detaljdata> detalj;

    /**
     * Gets the value of the aksjon property.
     * 
     * @return
     *     possible object is
     *     {@link Aksjonsdata }
     *     
     */
    public Aksjonsdata getAksjon() {
        return aksjon;
    }

    /**
     * Sets the value of the aksjon property.
     * 
     * @param value
     *     allowed object is
     *     {@link Aksjonsdata }
     *     
     */
    public void setAksjon(Aksjonsdata value) {
        this.aksjon = value;
    }

    /**
     * Gets the value of the total property.
     * 
     * @return
     *     possible object is
     *     {@link Totaldata }
     *     
     */
    public Totaldata getTotal() {
        return total;
    }

    /**
     * Sets the value of the total property.
     * 
     * @param value
     *     allowed object is
     *     {@link Totaldata }
     *     
     */
    public void setTotal(Totaldata value) {
        this.total = value;
    }

    /**
     * Gets the value of the periode property.
     * 
     * @return
     *     possible object is
     *     {@link Periodedata }
     *     
     */
    public Periodedata getPeriode() {
        return periode;
    }

    /**
     * Sets the value of the periode property.
     * 
     * @param value
     *     allowed object is
     *     {@link Periodedata }
     *     
     */
    public void setPeriode(Periodedata value) {
        this.periode = value;
    }

    /**
     * Gets the value of the grunnlag property.
     * 
     * @return
     *     possible object is
     *     {@link Grunnlagsdata }
     *     
     */
    public Grunnlagsdata getGrunnlag() {
        return grunnlag;
    }

    /**
     * Sets the value of the grunnlag property.
     * 
     * @param value
     *     allowed object is
     *     {@link Grunnlagsdata }
     *     
     */
    public void setGrunnlag(Grunnlagsdata value) {
        this.grunnlag = value;
    }

    /**
     * Gets the value of the detalj property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the detalj property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getDetalj().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link Detaljdata }
     * 
     * 
     */
    public List<Detaljdata> getDetalj() {
        if (detalj == null) {
            detalj = new ArrayList<Detaljdata>();
        }
        return this.detalj;
    }

}
