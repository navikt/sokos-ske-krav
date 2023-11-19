
package sokos.ske.krav.kontrakt;

import javax.xml.bind.JAXBElement;
import javax.xml.bind.annotation.XmlElementDecl;
import javax.xml.bind.annotation.XmlRegistry;
import javax.xml.namespace.QName;


/**
 * This object contains factory methods for each 
 * Java content interface and Java element interface 
 * generated in the sokos.ske.krav.kontrakt package. 
 * <p>An ObjectFactory allows you to programatically 
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
public class ObjectFactory {

    private final static QName _Avstemmingsdata_QNAME = new QName("http://nav.no/virksomhet/tjenester/avstemming/meldinger/v1", "avstemmingsdata");

    /**
     * Create a new ObjectFactory that can be used to create new instances of schema derived classes for package: sokos.ske.krav.kontrakt
     * 
     */
    public ObjectFactory() {
    }

    /**
     * Create an instance of {@link Avstemmingsdata }
     * 
     */
    public Avstemmingsdata createAvstemmingsdata() {
        return new Avstemmingsdata();
    }

    /**
     * Create an instance of {@link Detaljdata }
     * 
     */
    public Detaljdata createDetaljdata() {
        return new Detaljdata();
    }

    /**
     * Create an instance of {@link Periodedata }
     * 
     */
    public Periodedata createPeriodedata() {
        return new Periodedata();
    }

    /**
     * Create an instance of {@link Grunnlagsdata }
     * 
     */
    public Grunnlagsdata createGrunnlagsdata() {
        return new Grunnlagsdata();
    }

    /**
     * Create an instance of {@link Aksjonsdata }
     * 
     */
    public Aksjonsdata createAksjonsdata() {
        return new Aksjonsdata();
    }

    /**
     * Create an instance of {@link Totaldata }
     * 
     */
    public Totaldata createTotaldata() {
        return new Totaldata();
    }

    /**
     * Create an instance of {@link SendAsynkronAvstemmingsdataRequest }
     * 
     */
    public SendAsynkronAvstemmingsdataRequest createSendAsynkronAvstemmingsdataRequest() {
        return new SendAsynkronAvstemmingsdataRequest();
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link Avstemmingsdata }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://nav.no/virksomhet/tjenester/avstemming/meldinger/v1", name = "avstemmingsdata")
    public JAXBElement<Avstemmingsdata> createAvstemmingsdata(Avstemmingsdata value) {
        return new JAXBElement<Avstemmingsdata>(_Avstemmingsdata_QNAME, Avstemmingsdata.class, null, value);
    }

}
