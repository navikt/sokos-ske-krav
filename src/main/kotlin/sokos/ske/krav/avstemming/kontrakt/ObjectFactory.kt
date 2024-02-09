package sokos.ske.krav.avstemming.kontrakt

import javax.xml.bind.JAXBElement
import javax.xml.bind.annotation.XmlElementDecl
import javax.xml.bind.annotation.XmlRegistry
import javax.xml.namespace.QName

/**
 * This object contains factory methods for each
 * Java content interface and Java element interface
 * generated in the sokos.ske.krav.kontrakt package.
 *
 * An ObjectFactory allows you to programatically
 * construct new instances of the Java representation
 * for XML content. The Java representation of XML
 * content can consist of schema derived interfaces
 * and classes representing the binding of schema
 * type definitions, element declarations and model
 * groups.  Factory methods for each of these are
 * provided in this class.
 *
 */
@XmlRegistry
class ObjectFactory
/**
 * Create a new ObjectFactory that can be used to create new instances of schema derived classes for package: sokos.ske.krav.kontrakt
 *
 */
{
  /**
   * Create an instance of [Avstemmingsdata]
   *
   */
  fun createAvstemmingsdata(): Avstemmingsdata {
	return Avstemmingsdata()
  }

  /**
   * Create an instance of [Detaljdata]
   *
   */
  fun createDetaljdata(): Detaljdata {
	return Detaljdata()
  }

  /**
   * Create an instance of [Periodedata]
   *
   */
  fun createPeriodedata(): Periodedata {
	return Periodedata()
  }

  /**
   * Create an instance of [Grunnlagsdata]
   *
   */
  fun createGrunnlagsdata(): Grunnlagsdata {
	return Grunnlagsdata()
  }

  /**
   * Create an instance of [Aksjonsdata]
   *
   */
  fun createAksjonsdata(): Aksjonsdata {
	return Aksjonsdata()
  }

  /**
   * Create an instance of [Totaldata]
   *
   */
  fun createTotaldata(): Totaldata {
	return Totaldata()
  }

  /**
   * Create an instance of [SendAsynkronAvstemmingsdataRequest]
   *
   */
  fun createSendAsynkronAvstemmingsdataRequest(): SendAsynkronAvstemmingsdataRequest {
	return SendAsynkronAvstemmingsdataRequest()
  }

  /**
   * Create an instance of [JAXBElement]`<`[Avstemmingsdata]`>`}
   *
   */
  @XmlElementDecl(namespace = "http://nav.no/virksomhet/tjenester/avstemming/meldinger/v1", name = "avstemmingsdata")
  fun createAvstemmingsdata(value: Avstemmingsdata): JAXBElement<Avstemmingsdata> {
	return JAXBElement(_Avstemmingsdata_QNAME, Avstemmingsdata::class.java, null, value)
  }

  companion object {
	private val _Avstemmingsdata_QNAME = QName("http://nav.no/virksomhet/tjenester/avstemming/meldinger/v1", "avstemmingsdata")
  }
}
