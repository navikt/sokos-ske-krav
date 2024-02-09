package sokos.ske.krav.avstemming.kontrakt

import javax.xml.bind.annotation.*

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(
    name = "Aksjonsdata",
    namespace = "http://nav.no/virksomhet/tjenester/avstemming/meldinger/v1",
    propOrder = ["aksjonType", "kildeType", "avstemmingType", "avleverendeKomponentKode", "mottakendeKomponentKode", "underkomponentKode", "nokkelFom", "nokkelTom", "tidspunktAvstemmingTom", "avleverendeAvstemmingId", "brukerId"]
)
class Aksjonsdata {
    @XmlElement(required = true)
    @XmlSchemaType(name = "string")
    var aksjonType: AksjonType? = null

    @XmlElement(required = true)
    @XmlSchemaType(name = "string")

    var kildeType: KildeType? = null

    @XmlElement(required = true)
    @XmlSchemaType(name = "string")
    var avstemmingType: AvstemmingType? = null

    var avleverendeKomponentKode: String? = null

    @XmlElement(required = true)
    var mottakendeKomponentKode: String? = null
    var underkomponentKode: String? = null
    var nokkelFom: String? = null
    var nokkelTom: String? = null
    var tidspunktAvstemmingTom: String? = null
    var avleverendeAvstemmingId: String? = null

    @XmlElement(required = true)
    var brukerId: String? = null
}
