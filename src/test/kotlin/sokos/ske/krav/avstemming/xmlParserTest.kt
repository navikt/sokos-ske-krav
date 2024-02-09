package sokos.ske.krav.avstemming

import io.kotest.core.spec.style.FunSpec
import org.simpleframework.xml.Serializer
import org.simpleframework.xml.core.Persister
import sokos.ske.krav.avstemming.kontrakt.Avstemmingsdata
import kotlin.reflect.KClass
import kotlin.reflect.cast
import kotlin.reflect.full.createInstance

internal class xmlParserTest: FunSpec( {

    test("tester parsing") {
            val xmlToParse = avstemminsData()
            val serializer = Persister()
            val dataFetch = serializer.read(Avstemmingsdata::class.java, xmlToParse)
        println(dataFetch.total?.totalBelop)
        println(xmlToParse)
//            assertEquals(dataFetch.size, 8)
//            assertEquals(dataFetch.REC.first().name, "SELLER4_MOBILEPHONE")
    }

} )

fun avstemminsData(): String ="<v1:avstemmingsdata xmlns:v1=\"http://nav.no/virksomhet/tjenester/avstemming/meldinger/v1\">\n" +
        "  <aksjon>\n" +
        "    <aksjonType>AVSL</aksjonType>\n" +
        "    <kildeType>MOTT</kildeType>\n" +
        "    <avstemmingType>GRSN</avstemmingType>\n" +
        "       <avleverendeKomponentKode>string</avleverendeKomponentKode>\n" +
        "    <mottakendeKomponentKode>string</mottakendeKomponentKode>\n" +
        "       <underkomponentKode>string</underkomponentKode>\n" +
        "       <nokkelFom>string</nokkelFom>\n" +
        "       <nokkelTom>string</nokkelTom>\n" +
        "       <tidspunktAvstemmingTom>string</tidspunktAvstemmingTom>\n" +
        "       <avleverendeAvstemmingId>string</avleverendeAvstemmingId>\n" +
        "    <brukerId>string</brukerId>\n" +
        "  </aksjon>\n" +
        "   <total>\n" +
        "    <totalAntall>3</totalAntall>\n" +
        "       <totalBelop>1000.00</totalBelop>\n" +
        "       <fortegn>T</fortegn>\n" +
        "  </total>\n" +
        "   <periode>\n" +
        "    <datoAvstemtFom>string</datoAvstemtFom>\n" +
        "    <datoAvstemtTom>string</datoAvstemtTom>\n" +
        "  </periode>\n" +
        "   <grunnlag>\n" +
        "    <godkjentAntall>3</godkjentAntall>\n" +
        "       <godkjentBelop>1000.00</godkjentBelop>\n" +
        "       <godkjentFortegn>T</godkjentFortegn>\n" +
        "    <varselAntall>3</varselAntall>\n" +
        "       <varselBelop>1000.00</varselBelop>\n" +
        "       <varselFortegn>T</varselFortegn>\n" +
        "    <avvistAntall>3</avvistAntall>\n" +
        "       <avvistBelop>1000.00</avvistBelop>\n" +
        "       <avvistFortegn>F</avvistFortegn>\n" +
        "    <manglerAntall>3</manglerAntall>\n" +
        "       <manglerBelop>1000.00</manglerBelop>\n" +
        "       <manglerFortegn>T</manglerFortegn>\n" +
        "  </grunnlag>\n" +
        "  <!--Zero or more repetitions:-->\n" +
        "  <detalj>\n" +
        "    <detaljType>MANG</detaljType>\n" +
        "    <offnr>string</offnr>\n" +
        "    <avleverendeTransaksjonNokkel>string</avleverendeTransaksjonNokkel>\n" +
        "       <meldingKode>string</meldingKode>\n" +
        "       <alvorlighetsgrad>string</alvorlighetsgrad>\n" +
        "       <tekstMelding>string</tekstMelding>\n" +
        "    <tidspunkt>string</tidspunkt>\n" +
        "  </detalj>\n" +
        "</v1:avstemmingsdata>"