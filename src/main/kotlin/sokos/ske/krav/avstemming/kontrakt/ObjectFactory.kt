package sokos.ske.krav.avstemming.kontrakt

import javax.xml.bind.JAXBElement
import javax.xml.bind.annotation.XmlElementDecl
import javax.xml.bind.annotation.XmlRegistry
import javax.xml.namespace.QName


@XmlRegistry
class ObjectFactory
{
  
  fun createAvstemmingsdata(): Avstemmingsdata {
	return Avstemmingsdata()
  }

  
  fun createDetaljdata(): Detaljdata {
	return Detaljdata()
  }

  
  fun createPeriodedata(): Periodedata {
	return Periodedata()
  }

  
  fun createGrunnlagsdata(): Grunnlagsdata {
	return Grunnlagsdata()
  }

  
  fun createAksjonsdata(): Aksjonsdata {
	return Aksjonsdata()
  }

  
  fun createTotaldata(): Totaldata {
	return Totaldata()
  }

  
  fun createSendAsynkronAvstemmingsdataRequest(): SendAsynkronAvstemmingsdataRequest {
	return SendAsynkronAvstemmingsdataRequest()
  }

  
  @XmlElementDecl(namespace = "http://nav.no/virksomhet/tjenester/avstemming/meldinger/v1", name = "avstemmingsdata")
  fun createAvstemmingsdata(value: Avstemmingsdata): JAXBElement<Avstemmingsdata> {
	return JAXBElement(_Avstemmingsdata_QNAME, Avstemmingsdata::class.java, null, value)
  }

  companion object {
	private val _Avstemmingsdata_QNAME = QName("http://nav.no/virksomhet/tjenester/avstemming/meldinger/v1", "avstemmingsdata")
  }
}
