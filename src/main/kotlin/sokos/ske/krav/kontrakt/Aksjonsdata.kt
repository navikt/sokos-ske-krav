package sokos.ske.krav.kontrakt

import javax.xml.bind.annotation.XmlAccessType
import javax.xml.bind.annotation.XmlAccessorType
import javax.xml.bind.annotation.XmlElement
import javax.xml.bind.annotation.XmlSchemaType
import javax.xml.bind.annotation.XmlType

/**
 * Enhver avstemming må initieres og avsluttes med en 110-record, på det formatet som er beskrevet her
 *
 *
 * Java class for Aksjonsdata complex type.
 *
 *
 * The following schema fragment specifies the expected content contained within this class.
 *
 * <pre>
 * &lt;complexType name="Aksjonsdata">
 * &lt;complexContent>
 * &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 * &lt;sequence>
 * &lt;element name="aksjonType" type="{http://nav.no/virksomhet/tjenester/avstemming/meldinger/v1}AksjonType"/>
 * &lt;element name="kildeType" type="{http://nav.no/virksomhet/tjenester/avstemming/meldinger/v1}KildeType"/>
 * &lt;element name="avstemmingType" type="{http://nav.no/virksomhet/tjenester/avstemming/meldinger/v1}AvstemmingType"/>
 * &lt;element name="avleverendeKomponentKode" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 * &lt;element name="mottakendeKomponentKode" type="{http://www.w3.org/2001/XMLSchema}string"/>
 * &lt;element name="underkomponentKode" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 * &lt;element name="nokkelFom" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 * &lt;element name="nokkelTom" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 * &lt;element name="tidspunktAvstemmingTom" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 * &lt;element name="avleverendeAvstemmingId" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 * &lt;element name="brukerId" type="{http://www.w3.org/2001/XMLSchema}string"/>
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
    name = "Aksjonsdata",
    namespace = "http://nav.no/virksomhet/tjenester/avstemming/meldinger/v1",
    propOrder = ["aksjonType", "kildeType", "avstemmingType", "avleverendeKomponentKode", "mottakendeKomponentKode", "underkomponentKode", "nokkelFom", "nokkelTom", "tidspunktAvstemmingTom", "avleverendeAvstemmingId", "brukerId"]
)
class Aksjonsdata {
    /**
     * Gets the value of the aksjonType property.
     *
     * @return
     * possible object is
     * [AksjonType]
     */
    /**
     * Sets the value of the aksjonType property.
     *
     * @param value
     * allowed object is
     * [AksjonType]
     */
    @XmlElement(required = true)
    @XmlSchemaType(name = "string")
    var aksjonType: AksjonType? = null
    /**
     * Gets the value of the kildeType property.
     *
     * @return
     * possible object is
     * [KildeType]
     */
    /**
     * Sets the value of the kildeType property.
     *
     * @param value
     * allowed object is
     * [KildeType]
     */
    @XmlElement(required = true)
    @XmlSchemaType(name = "string")
    var kildeType: KildeType? = null
    /**
     * Gets the value of the avstemmingType property.
     *
     * @return
     * possible object is
     * [AvstemmingType]
     */
    /**
     * Sets the value of the avstemmingType property.
     *
     * @param value
     * allowed object is
     * [AvstemmingType]
     */
    @XmlElement(required = true)
    @XmlSchemaType(name = "string")
    var avstemmingType: AvstemmingType? = null
    /**
     * Gets the value of the avleverendeKomponentKode property.
     *
     * @return
     * possible object is
     * [String]
     */
    /**
     * Sets the value of the avleverendeKomponentKode property.
     *
     * @param value
     * allowed object is
     * [String]
     */
    var avleverendeKomponentKode: String? = null
    /**
     * Gets the value of the mottakendeKomponentKode property.
     *
     * @return
     * possible object is
     * [String]
     */
    /**
     * Sets the value of the mottakendeKomponentKode property.
     *
     * @param value
     * allowed object is
     * [String]
     */
    @XmlElement(required = true)
    var mottakendeKomponentKode: String? = null
    /**
     * Gets the value of the underkomponentKode property.
     *
     * @return
     * possible object is
     * [String]
     */
    /**
     * Sets the value of the underkomponentKode property.
     *
     * @param value
     * allowed object is
     * [String]
     */
    var underkomponentKode: String? = null
    /**
     * Gets the value of the nokkelFom property.
     *
     * @return
     * possible object is
     * [String]
     */
    /**
     * Sets the value of the nokkelFom property.
     *
     * @param value
     * allowed object is
     * [String]
     */
    var nokkelFom: String? = null
    /**
     * Gets the value of the nokkelTom property.
     *
     * @return
     * possible object is
     * [String]
     */
    /**
     * Sets the value of the nokkelTom property.
     *
     * @param value
     * allowed object is
     * [String]
     */
    var nokkelTom: String? = null
    /**
     * Gets the value of the tidspunktAvstemmingTom property.
     *
     * @return
     * possible object is
     * [String]
     */
    /**
     * Sets the value of the tidspunktAvstemmingTom property.
     *
     * @param value
     * allowed object is
     * [String]
     */
    var tidspunktAvstemmingTom: String? = null
    /**
     * Gets the value of the avleverendeAvstemmingId property.
     *
     * @return
     * possible object is
     * [String]
     */
    /**
     * Sets the value of the avleverendeAvstemmingId property.
     *
     * @param value
     * allowed object is
     * [String]
     */
    var avleverendeAvstemmingId: String? = null
    /**
     * Gets the value of the brukerId property.
     *
     * @return
     * possible object is
     * [String]
     */
    /**
     * Sets the value of the brukerId property.
     *
     * @param value
     * allowed object is
     * [String]
     */
    @XmlElement(required = true)
    var brukerId: String? = null
}
