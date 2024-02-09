package sokos.ske.krav.avstemming.kontrakt

import java.math.BigDecimal
import javax.xml.bind.annotation.XmlAccessType
import javax.xml.bind.annotation.XmlAccessorType
import javax.xml.bind.annotation.XmlSchemaType
import javax.xml.bind.annotation.XmlType


@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(
  name = "Totaldata", namespace = "http://nav.no/virksomhet/tjenester/avstemming/meldinger/v1", propOrder = ["totalAntall", "totalBelop", "fortegn"
  ]
)
class Totaldata {

  var totalAntall: Int = 0
  var totalBelop: BigDecimal? = null
  
  @XmlSchemaType(name = "string")
  var fortegn: Fortegn? = null
}
