
package sokos.ske.krav.kontrakt;

import java.math.BigDecimal;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlSchemaType;
import javax.xml.bind.annotation.XmlType;


/**
 * Grunnlagsrecord (id-130) for å skille mellom antall godkjente og avviste meldinger, antall godkjente med varsel og antall meldinger hvor avleverende system ikke har mottatt kvitteringsmelding.
 * 
 * <p>Java class for Grunnlagsdata complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="Grunnlagsdata">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="godkjentAntall" type="{http://www.w3.org/2001/XMLSchema}int"/>
 *         &lt;element name="godkjentBelop" type="{http://www.w3.org/2001/XMLSchema}decimal" minOccurs="0"/>
 *         &lt;element name="godkjentFortegn" type="{http://nav.no/virksomhet/tjenester/avstemming/meldinger/v1}Fortegn" minOccurs="0"/>
 *         &lt;element name="varselAntall" type="{http://www.w3.org/2001/XMLSchema}int"/>
 *         &lt;element name="varselBelop" type="{http://www.w3.org/2001/XMLSchema}decimal" minOccurs="0"/>
 *         &lt;element name="varselFortegn" type="{http://nav.no/virksomhet/tjenester/avstemming/meldinger/v1}Fortegn" minOccurs="0"/>
 *         &lt;element name="avvistAntall" type="{http://www.w3.org/2001/XMLSchema}int"/>
 *         &lt;element name="avvistBelop" type="{http://www.w3.org/2001/XMLSchema}decimal" minOccurs="0"/>
 *         &lt;element name="avvistFortegn" type="{http://nav.no/virksomhet/tjenester/avstemming/meldinger/v1}Fortegn" minOccurs="0"/>
 *         &lt;element name="manglerAntall" type="{http://www.w3.org/2001/XMLSchema}int"/>
 *         &lt;element name="manglerBelop" type="{http://www.w3.org/2001/XMLSchema}decimal" minOccurs="0"/>
 *         &lt;element name="manglerFortegn" type="{http://nav.no/virksomhet/tjenester/avstemming/meldinger/v1}Fortegn" minOccurs="0"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "Grunnlagsdata", namespace = "http://nav.no/virksomhet/tjenester/avstemming/meldinger/v1", propOrder = {
    "godkjentAntall",
    "godkjentBelop",
    "godkjentFortegn",
    "varselAntall",
    "varselBelop",
    "varselFortegn",
    "avvistAntall",
    "avvistBelop",
    "avvistFortegn",
    "manglerAntall",
    "manglerBelop",
    "manglerFortegn"
})
public class Grunnlagsdata {

    protected int godkjentAntall;
    protected BigDecimal godkjentBelop;
    @XmlSchemaType(name = "string")
    protected Fortegn godkjentFortegn;
    protected int varselAntall;
    protected BigDecimal varselBelop;
    @XmlSchemaType(name = "string")
    protected Fortegn varselFortegn;
    protected int avvistAntall;
    protected BigDecimal avvistBelop;
    @XmlSchemaType(name = "string")
    protected Fortegn avvistFortegn;
    protected int manglerAntall;
    protected BigDecimal manglerBelop;
    @XmlSchemaType(name = "string")
    protected Fortegn manglerFortegn;

    /**
     * Gets the value of the godkjentAntall property.
     * 
     */
    public int getGodkjentAntall() {
        return godkjentAntall;
    }

    /**
     * Sets the value of the godkjentAntall property.
     * 
     */
    public void setGodkjentAntall(int value) {
        this.godkjentAntall = value;
    }

    /**
     * Gets the value of the godkjentBelop property.
     * 
     * @return
     *     possible object is
     *     {@link BigDecimal }
     *     
     */
    public BigDecimal getGodkjentBelop() {
        return godkjentBelop;
    }

    /**
     * Sets the value of the godkjentBelop property.
     * 
     * @param value
     *     allowed object is
     *     {@link BigDecimal }
     *     
     */
    public void setGodkjentBelop(BigDecimal value) {
        this.godkjentBelop = value;
    }

    /**
     * Gets the value of the godkjentFortegn property.
     * 
     * @return
     *     possible object is
     *     {@link Fortegn }
     *     
     */
    public Fortegn getGodkjentFortegn() {
        return godkjentFortegn;
    }

    /**
     * Sets the value of the godkjentFortegn property.
     * 
     * @param value
     *     allowed object is
     *     {@link Fortegn }
     *     
     */
    public void setGodkjentFortegn(Fortegn value) {
        this.godkjentFortegn = value;
    }

    /**
     * Gets the value of the varselAntall property.
     * 
     */
    public int getVarselAntall() {
        return varselAntall;
    }

    /**
     * Sets the value of the varselAntall property.
     * 
     */
    public void setVarselAntall(int value) {
        this.varselAntall = value;
    }

    /**
     * Gets the value of the varselBelop property.
     * 
     * @return
     *     possible object is
     *     {@link BigDecimal }
     *     
     */
    public BigDecimal getVarselBelop() {
        return varselBelop;
    }

    /**
     * Sets the value of the varselBelop property.
     * 
     * @param value
     *     allowed object is
     *     {@link BigDecimal }
     *     
     */
    public void setVarselBelop(BigDecimal value) {
        this.varselBelop = value;
    }

    /**
     * Gets the value of the varselFortegn property.
     * 
     * @return
     *     possible object is
     *     {@link Fortegn }
     *     
     */
    public Fortegn getVarselFortegn() {
        return varselFortegn;
    }

    /**
     * Sets the value of the varselFortegn property.
     * 
     * @param value
     *     allowed object is
     *     {@link Fortegn }
     *     
     */
    public void setVarselFortegn(Fortegn value) {
        this.varselFortegn = value;
    }

    /**
     * Gets the value of the avvistAntall property.
     * 
     */
    public int getAvvistAntall() {
        return avvistAntall;
    }

    /**
     * Sets the value of the avvistAntall property.
     * 
     */
    public void setAvvistAntall(int value) {
        this.avvistAntall = value;
    }

    /**
     * Gets the value of the avvistBelop property.
     * 
     * @return
     *     possible object is
     *     {@link BigDecimal }
     *     
     */
    public BigDecimal getAvvistBelop() {
        return avvistBelop;
    }

    /**
     * Sets the value of the avvistBelop property.
     * 
     * @param value
     *     allowed object is
     *     {@link BigDecimal }
     *     
     */
    public void setAvvistBelop(BigDecimal value) {
        this.avvistBelop = value;
    }

    /**
     * Gets the value of the avvistFortegn property.
     * 
     * @return
     *     possible object is
     *     {@link Fortegn }
     *     
     */
    public Fortegn getAvvistFortegn() {
        return avvistFortegn;
    }

    /**
     * Sets the value of the avvistFortegn property.
     * 
     * @param value
     *     allowed object is
     *     {@link Fortegn }
     *     
     */
    public void setAvvistFortegn(Fortegn value) {
        this.avvistFortegn = value;
    }

    /**
     * Gets the value of the manglerAntall property.
     * 
     */
    public int getManglerAntall() {
        return manglerAntall;
    }

    /**
     * Sets the value of the manglerAntall property.
     * 
     */
    public void setManglerAntall(int value) {
        this.manglerAntall = value;
    }

    /**
     * Gets the value of the manglerBelop property.
     * 
     * @return
     *     possible object is
     *     {@link BigDecimal }
     *     
     */
    public BigDecimal getManglerBelop() {
        return manglerBelop;
    }

    /**
     * Sets the value of the manglerBelop property.
     * 
     * @param value
     *     allowed object is
     *     {@link BigDecimal }
     *     
     */
    public void setManglerBelop(BigDecimal value) {
        this.manglerBelop = value;
    }

    /**
     * Gets the value of the manglerFortegn property.
     * 
     * @return
     *     possible object is
     *     {@link Fortegn }
     *     
     */
    public Fortegn getManglerFortegn() {
        return manglerFortegn;
    }

    /**
     * Sets the value of the manglerFortegn property.
     * 
     * @param value
     *     allowed object is
     *     {@link Fortegn }
     *     
     */
    public void setManglerFortegn(Fortegn value) {
        this.manglerFortegn = value;
    }

}
