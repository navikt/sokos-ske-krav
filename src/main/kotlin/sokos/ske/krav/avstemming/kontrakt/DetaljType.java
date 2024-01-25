
package sokos.ske.krav.avstemming.kontrakt;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for DetaljType.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="DetaljType">
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
 *     &lt;enumeration value="VARS"/>
 *     &lt;enumeration value="AVVI"/>
 *     &lt;enumeration value="MANG"/>
 *   &lt;/restriction>
 * &lt;/simpleType>
 * </pre>
 * 
 */
@XmlType(name = "DetaljType", namespace = "http://nav.no/virksomhet/tjenester/avstemming/meldinger/v1")
@XmlEnum
public enum DetaljType {


    /**
     * Godkjent med varsel
     * 
     */
    VARS,

    /**
     * Avvist
     * 
     */
    AVVI,

    /**
     * Manglende kvittering
     * 
     */
    MANG;

    public String value() {
        return name();
    }

    public static DetaljType fromValue(String v) {
        return valueOf(v);
    }

}
