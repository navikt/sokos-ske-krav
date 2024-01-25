
package sokos.ske.krav.avstemming.kontrakt;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for AvstemmingType.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="AvstemmingType">
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
 *     &lt;enumeration value="GRSN"/>
 *     &lt;enumeration value="KONS"/>
 *     &lt;enumeration value="PERI"/>
 *   &lt;/restriction>
 * &lt;/simpleType>
 * </pre>
 * 
 */
@XmlType(name = "AvstemmingType", namespace = "http://nav.no/virksomhet/tjenester/avstemming/meldinger/v1")
@XmlEnum
public enum AvstemmingType {


    /**
     * Grensesnittavstemming
     * 
     */
    GRSN,

    /**
     * Konsistensavstemming
     * 
     */
    KONS,

    /**
     * Periodeavstemming
     * 
     */
    PERI;

    public String value() {
        return name();
    }

    public static AvstemmingType fromValue(String v) {
        return valueOf(v);
    }

}
