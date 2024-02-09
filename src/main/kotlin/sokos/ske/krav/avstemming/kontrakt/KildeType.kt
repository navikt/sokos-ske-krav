
package sokos.ske.krav.avstemming.kontrakt;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for KildeType.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="KildeType">
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
 *     &lt;enumeration value="AVLEV"/>
 *     &lt;enumeration value="MOTT"/>
 *   &lt;/restriction>
 * &lt;/simpleType>
 * </pre>
 * 
 */
@XmlType(name = "KildeType", namespace = "http://nav.no/virksomhet/tjenester/avstemming/meldinger/v1")
@XmlEnum
public enum KildeType {


    /**
     * Avleverende komponent
     * 
     */
    AVLEV,

    /**
     * Mottakende komponent
     * 
     */
    MOTT;

    public String value() {
        return name();
    }

    public static KildeType fromValue(String v) {
        return valueOf(v);
    }

}
