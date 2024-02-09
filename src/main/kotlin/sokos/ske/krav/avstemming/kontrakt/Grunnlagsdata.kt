package sokos.ske.krav.avstemming.kontrakt

import java.math.BigDecimal
import javax.xml.bind.annotation.XmlAccessType
import javax.xml.bind.annotation.XmlAccessorType
import javax.xml.bind.annotation.XmlSchemaType
import javax.xml.bind.annotation.XmlType


@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(
  name = "Grunnlagsdata",
  namespace = "http://nav.no/virksomhet/tjenester/avstemming/meldinger/v1",
  propOrder = ["godkjentAntall", "godkjentBelop", "godkjentFortegn", "varselAntall", "varselBelop", "varselFortegn", "avvistAntall", "avvistBelop", "avvistFortegn", "manglerAntall", "manglerBelop", "manglerFortegn"
  ]
)
class Grunnlagsdata {

  var godkjentAntall: Int = 0
  var godkjentBelop: BigDecimal? = null

  @XmlSchemaType(name = "string")
  var godkjentFortegn: Fortegn? = null

  var varselAntall: Int = 0
  var varselBelop: BigDecimal? = null

  @XmlSchemaType(name = "string")
  var varselFortegn: Fortegn? = null

  var avvistAntall: Int = 0
  var avvistBelop: BigDecimal? = null

  @XmlSchemaType(name = "string")
  var avvistFortegn: Fortegn? = null
  
  var manglerAntall: Int = 0
  var manglerBelop: BigDecimal? = null

  @XmlSchemaType(name = "string")
  var manglerFortegn: Fortegn? = null
}
