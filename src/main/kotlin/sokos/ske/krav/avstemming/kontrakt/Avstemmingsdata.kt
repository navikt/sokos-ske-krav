package sokos.ske.krav.avstemming.kontrakt

import org.simpleframework.xml.Element
import org.simpleframework.xml.ElementList
import javax.xml.bind.annotation.XmlAccessType
import javax.xml.bind.annotation.XmlAccessorType
import javax.xml.bind.annotation.XmlElement
import javax.xml.bind.annotation.XmlType

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(
  name = "Avstemmingsdata", namespace = "http://nav.no/virksomhet/tjenester/avstemming/meldinger/v1", propOrder = ["aksjon", "total", "periode", "grunnlag", "detalj"
  ]
)
class Avstemmingsdata {

  @field:Element(name = "aksjon", required = false)
  var aksjon: Aksjonsdata? = null

  @field:Element(name = "total", required = false)
  var total: Totaldata? = null
  @field:Element(name = "periode", required = false)
  var periode: Periodedata? = null
  @field:Element(name = "grunnlag", required = false)
  var grunnlag: Grunnlagsdata? = null
  @field:ElementList(name = "detalj", required = true)
  var detalj: List<Detaljdata>? = null

}
