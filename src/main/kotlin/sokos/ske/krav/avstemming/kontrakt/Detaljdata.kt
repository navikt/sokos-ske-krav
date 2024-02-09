package sokos.ske.krav.avstemming.kontrakt

import org.simpleframework.xml.Element
import javax.xml.bind.annotation.XmlAccessType
import javax.xml.bind.annotation.XmlAccessorType
import javax.xml.bind.annotation.XmlSchemaType
import javax.xml.bind.annotation.XmlType

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
