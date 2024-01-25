
package sokos.ske.krav.avstemming.kontrakt;

import javax.xml.bind.annotation.*;


/**
 * Enhver avstemming må initieres og avsluttes med en 110-record, på det formatet som er beskrevet her
 * 
 * <p>Java class for Aksjonsdata complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="Aksjonsdata">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="aksjonType" type="{http://nav.no/virksomhet/tjenester/avstemming/meldinger/v1}AksjonType"/>
 *         &lt;element name="kildeType" type="{http://nav.no/virksomhet/tjenester/avstemming/meldinger/v1}KildeType"/>
 *         &lt;element name="avstemmingType" type="{http://nav.no/virksomhet/tjenester/avstemming/meldinger/v1}AvstemmingType"/>
 *         &lt;element name="avleverendeKomponentKode" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         &lt;element name="mottakendeKomponentKode" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="underkomponentKode" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         &lt;element name="nokkelFom" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         &lt;element name="nokkelTom" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         &lt;element name="tidspunktAvstemmingTom" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         &lt;element name="avleverendeAvstemmingId" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         &lt;element name="brukerId" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "Aksjonsdata", namespace = "http://nav.no/virksomhet/tjenester/avstemming/meldinger/v1", propOrder = {
    "aksjonType",
    "kildeType",
    "avstemmingType",
    "avleverendeKomponentKode",
    "mottakendeKomponentKode",
    "underkomponentKode",
    "nokkelFom",
    "nokkelTom",
    "tidspunktAvstemmingTom",
    "avleverendeAvstemmingId",
    "brukerId"
})
public class AksjonsdataJava {

    @XmlElement(required = true)
    @XmlSchemaType(name = "string")
    protected AksjonType aksjonType;
    @XmlElement(required = true)
    @XmlSchemaType(name = "string")
    protected KildeType kildeType;
    @XmlElement(required = true)
    @XmlSchemaType(name = "string")
    protected AvstemmingType avstemmingType;
    protected String avleverendeKomponentKode;
    @XmlElement(required = true)
    protected String mottakendeKomponentKode;
    protected String underkomponentKode;
    protected String nokkelFom;
    protected String nokkelTom;
    protected String tidspunktAvstemmingTom;
    protected String avleverendeAvstemmingId;
    @XmlElement(required = true)
    protected String brukerId;

    /**
     * Gets the value of the aksjonType property.
     * 
     * @return
     *     possible object is
     *     {@link AksjonType }
     *     
     */
    public AksjonType getAksjonType() {
        return aksjonType;
    }

    /**
     * Sets the value of the aksjonType property.
     * 
     * @param value
     *     allowed object is
     *     {@link AksjonType }
     *     
     */
    public void setAksjonType(AksjonType value) {
        this.aksjonType = value;
    }

    /**
     * Gets the value of the kildeType property.
     * 
     * @return
     *     possible object is
     *     {@link KildeType }
     *     
     */
    public KildeType getKildeType() {
        return kildeType;
    }

    /**
     * Sets the value of the kildeType property.
     * 
     * @param value
     *     allowed object is
     *     {@link KildeType }
     *     
     */
    public void setKildeType(KildeType value) {
        this.kildeType = value;
    }

    /**
     * Gets the value of the avstemmingType property.
     * 
     * @return
     *     possible object is
     *     {@link AvstemmingType }
     *     
     */
    public AvstemmingType getAvstemmingType() {
        return avstemmingType;
    }

    /**
     * Sets the value of the avstemmingType property.
     * 
     * @param value
     *     allowed object is
     *     {@link AvstemmingType }
     *     
     */
    public void setAvstemmingType(AvstemmingType value) {
        this.avstemmingType = value;
    }

    /**
     * Gets the value of the avleverendeKomponentKode property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getAvleverendeKomponentKode() {
        return avleverendeKomponentKode;
    }

    /**
     * Sets the value of the avleverendeKomponentKode property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setAvleverendeKomponentKode(String value) {
        this.avleverendeKomponentKode = value;
    }

    /**
     * Gets the value of the mottakendeKomponentKode property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getMottakendeKomponentKode() {
        return mottakendeKomponentKode;
    }

    /**
     * Sets the value of the mottakendeKomponentKode property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setMottakendeKomponentKode(String value) {
        this.mottakendeKomponentKode = value;
    }

    /**
     * Gets the value of the underkomponentKode property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getUnderkomponentKode() {
        return underkomponentKode;
    }

    /**
     * Sets the value of the underkomponentKode property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setUnderkomponentKode(String value) {
        this.underkomponentKode = value;
    }

    /**
     * Gets the value of the nokkelFom property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getNokkelFom() {
        return nokkelFom;
    }

    /**
     * Sets the value of the nokkelFom property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setNokkelFom(String value) {
        this.nokkelFom = value;
    }

    /**
     * Gets the value of the nokkelTom property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getNokkelTom() {
        return nokkelTom;
    }

    /**
     * Sets the value of the nokkelTom property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setNokkelTom(String value) {
        this.nokkelTom = value;
    }

    /**
     * Gets the value of the tidspunktAvstemmingTom property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getTidspunktAvstemmingTom() {
        return tidspunktAvstemmingTom;
    }

    /**
     * Sets the value of the tidspunktAvstemmingTom property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setTidspunktAvstemmingTom(String value) {
        this.tidspunktAvstemmingTom = value;
    }

    /**
     * Gets the value of the avleverendeAvstemmingId property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getAvleverendeAvstemmingId() {
        return avleverendeAvstemmingId;
    }

    /**
     * Sets the value of the avleverendeAvstemmingId property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setAvleverendeAvstemmingId(String value) {
        this.avleverendeAvstemmingId = value;
    }

    /**
     * Gets the value of the brukerId property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getBrukerId() {
        return brukerId;
    }

    /**
     * Sets the value of the brukerId property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setBrukerId(String value) {
        this.brukerId = value;
    }

}
