
package sokos.ske.krav.avstemming.kontrakt;

import javax.xml.bind.annotation.*;


/**
 * Grensesnittavstemmingen kan inneholde detaljer på avviste meldinger, godkjente meldinger med varsel og meldinger hvor avleverende system ikke har mottatt kvitteringsmelding. Det kan ikke overføres 140-data uten at det også er overført en id-130. Det må overføres ID140 dersom det finnes avviste meldinger eller meldinger hvor det mangler kvittering.
 * 
 * <p>Java class for Detaljdata complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="Detaljdata">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="detaljType" type="{http://nav.no/virksomhet/tjenester/avstemming/meldinger/v1}DetaljType"/>
 *         &lt;element name="offnr" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="avleverendeTransaksjonNokkel" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="meldingKode" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         &lt;element name="alvorlighetsgrad" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         &lt;element name="tekstMelding" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         &lt;element name="tidspunkt" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "Detaljdata", namespace = "http://nav.no/virksomhet/tjenester/avstemming/meldinger/v1", propOrder = {
    "detaljType",
    "offnr",
    "avleverendeTransaksjonNokkel",
    "meldingKode",
    "alvorlighetsgrad",
    "tekstMelding",
    "tidspunkt"
})
public class Detaljdata {

    @XmlElement(required = true)
    @XmlSchemaType(name = "string")
    protected DetaljType detaljType;
    @XmlElement(required = true)
    protected String offnr;
    @XmlElement(required = true)
    protected String avleverendeTransaksjonNokkel;
    protected String meldingKode;
    protected String alvorlighetsgrad;
    protected String tekstMelding;
    @XmlElement(required = true)
    protected String tidspunkt;

    /**
     * Gets the value of the detaljType property.
     * 
     * @return
     *     possible object is
     *     {@link DetaljType }
     *     
     */
    public DetaljType getDetaljType() {
        return detaljType;
    }

    /**
     * Sets the value of the detaljType property.
     * 
     * @param value
     *     allowed object is
     *     {@link DetaljType }
     *     
     */
    public void setDetaljType(DetaljType value) {
        this.detaljType = value;
    }

    /**
     * Gets the value of the offnr property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getOffnr() {
        return offnr;
    }

    /**
     * Sets the value of the offnr property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setOffnr(String value) {
        this.offnr = value;
    }

    /**
     * Gets the value of the avleverendeTransaksjonNokkel property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getAvleverendeTransaksjonNokkel() {
        return avleverendeTransaksjonNokkel;
    }

    /**
     * Sets the value of the avleverendeTransaksjonNokkel property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setAvleverendeTransaksjonNokkel(String value) {
        this.avleverendeTransaksjonNokkel = value;
    }

    /**
     * Gets the value of the meldingKode property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getMeldingKode() {
        return meldingKode;
    }

    /**
     * Sets the value of the meldingKode property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setMeldingKode(String value) {
        this.meldingKode = value;
    }

    /**
     * Gets the value of the alvorlighetsgrad property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getAlvorlighetsgrad() {
        return alvorlighetsgrad;
    }

    /**
     * Sets the value of the alvorlighetsgrad property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setAlvorlighetsgrad(String value) {
        this.alvorlighetsgrad = value;
    }

    /**
     * Gets the value of the tekstMelding property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getTekstMelding() {
        return tekstMelding;
    }

    /**
     * Sets the value of the tekstMelding property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setTekstMelding(String value) {
        this.tekstMelding = value;
    }

    /**
     * Gets the value of the tidspunkt property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getTidspunkt() {
        return tidspunkt;
    }

    /**
     * Sets the value of the tidspunkt property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setTidspunkt(String value) {
        this.tidspunkt = value;
    }

}
