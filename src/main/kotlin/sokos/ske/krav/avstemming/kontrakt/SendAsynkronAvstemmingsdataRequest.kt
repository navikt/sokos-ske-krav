package sokos.ske.krav.avstemming.kontrakt

import javax.xml.bind.annotation.XmlAccessType
import javax.xml.bind.annotation.XmlAccessorType
import javax.xml.bind.annotation.XmlElement
import javax.xml.bind.annotation.XmlType


@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(
  name = "SendAsynkronAvstemmingsdataRequest", namespace = "http://nav.no/virksomhet/tjenester/avstemming/meldinger/v1", propOrder = ["avstemmingsdata"
  ]
)
class SendAsynkronAvstemmingsdataRequest {
  
  
  @XmlElement(required = true)
  var avstemmingsdata: Avstemmingsdata? = null
}
