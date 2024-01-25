
package sokos.ske.krav.avstemming.kontrakt;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for Fortegn.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="Fortegn">
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
 *     &lt;enumeration value="T"/>
 *     &lt;enumeration value="F"/>
 *   &lt;/restriction>
 * &lt;/simpleType>
 * </pre>
 * 
 */
@XmlType(name = "Fortegn", namespace = "http://nav.no/virksomhet/tjenester/avstemming/meldinger/v1")
@XmlEnum
public enum Fortegn {


    /**
     * Tillegg
     * 
     */
    T,

    /**
     * Fradrag
     * 
     */
    F;

    public String value() {
        return name();
    }

    public static Fortegn fromValue(String v) {
        return valueOf(v);
    }

}
