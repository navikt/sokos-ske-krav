package sokos.ske.krav.avstemming.kontrakt

import javax.xml.bind.annotation.XmlAccessType
import javax.xml.bind.annotation.XmlAccessorType
import javax.xml.bind.annotation.XmlElement
import javax.xml.bind.annotation.XmlType


@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(
  name = "Periodedata", namespace = "http://nav.no/virksomhet/tjenester/avstemming/meldinger/v1", propOrder = ["datoAvstemtFom", "datoAvstemtTom"
  ]
)
data class Periodedata (

  @XmlElement(required = true)
  var datoAvstemtFom: String? = null,

  @XmlElement(required = true)
  var datoAvstemtTom: String? = null
)
