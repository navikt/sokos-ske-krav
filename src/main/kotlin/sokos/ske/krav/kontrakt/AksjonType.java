
package sokos.ske.krav.kontrakt;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for AksjonType.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="AksjonType">
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
 *     &lt;enumeration value="START"/>
 *     &lt;enumeration value="DATA"/>
 *     &lt;enumeration value="AVSL"/>
 *     &lt;enumeration value="HENT"/>
 *   &lt;/restriction>
 * &lt;/simpleType>
 * </pre>
 * 
 */
@XmlType(name = "AksjonType", namespace = "http://nav.no/virksomhet/tjenester/avstemming/meldinger/v1")
@XmlEnum
public enum AksjonType {


    /**
     * Aksjonskoden settes til ’START’ når avstemmingen starter
     * 
     */
    START,

    /**
     * Aksjonskode ’DATA’ benyttes når selve avstemmingsdatene skal overføres.
     * 
     */
    DATA,

    /**
     * ’AVSL’ når alle avstemmingsdata er overført.
     * 
     */
    AVSL,

    /**
     * Aksjonskode ’HENT’ benyttes dersom mottakende komponent har behov for å finne avstemminger som er påbegynt fra avleverende komponent, men mangler data fra mottakende komponent.
     * 
     */
    HENT;

    public String value() {
        return name();
    }

    public static AksjonType fromValue(String v) {
        return valueOf(v);
    }

}
