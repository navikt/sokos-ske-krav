package sokos.ske.krav.avstemming.kontrakt

import org.simpleframework.xml.Element
import javax.xml.bind.annotation.XmlAccessType
import javax.xml.bind.annotation.XmlAccessorType
import javax.xml.bind.annotation.XmlElement
import javax.xml.bind.annotation.XmlSchemaType
import javax.xml.bind.annotation.XmlType

/**
 * Grensesnittavstemmingen kan inneholde detaljer på avviste meldinger, godkjente meldinger med varsel og meldinger hvor avleverende system ikke har mottatt kvitteringsmelding. Det kan ikke overføres 140-data uten at det også er overført en id-130. Det må overføres ID140 dersom det finnes avviste meldinger eller meldinger hvor det mangler kvittering.
 *
 *
 * Java class for Detaljdata complex type.
 *
 *
 * The following schema fragment specifies the expected content contained within this class.
 *
 * <pre>
 * &lt;complexType name="Detaljdata">
 * &lt;complexContent>
 * &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 * &lt;sequence>
 * &lt;element name="detaljType" type="{http://nav.no/virksomhet/tjenester/avstemming/meldinger/v1}DetaljType"/>
 * &lt;element name="offnr" type="{http://www.w3.org/2001/XMLSchema}string"/>
 * &lt;element name="avleverendeTransaksjonNokkel" type="{http://www.w3.org/2001/XMLSchema}string"/>
 * &lt;element name="meldingKode" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 * &lt;element name="alvorlighetsgrad" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 * &lt;element name="tekstMelding" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 * &lt;element name="tidspunkt" type="{http://www.w3.org/2001/XMLSchema}string"/>
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
  name = "Detaljdata", namespace = "http://nav.no/virksomhet/tjenester/avstemming/meldinger/v1",
  propOrder = ["detaljType", "offnr", "avleverendeTransaksjonNokkel", "meldingKode", "alvorlighetsgrad",
    "tekstMelding", "tidspunkt"
  ]
)
class Detaljdata {

  @field:Element(name = "detaljType", required = false)
  @XmlSchemaType(name = "string")
  var detaljType: DetaljType? = null

  @field:Element(name = "offnr", required = false)
  var offnr: String = "hei"

  @field:Element(name = "avleverendeTransaksjonNokkel", required = false)
  var avleverendeTransaksjonNokkel: String? = null

  @field:Element(name = "alvorlighetsgrad", required = false)
  var alvorlighetsgrad: String? = null
  @field:Element(name = "tekstMelding", required = false)
  var tekstMelding: String? = null

  @field:Element(name = "tidspunkt", required = false)
  var tidspunkt: String? = null
}
