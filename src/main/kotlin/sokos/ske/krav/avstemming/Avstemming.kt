package sokos.ske.krav.avstemming

import org.simpleframework.xml.core.Persister
import sokos.ske.krav.avstemming.kontrakt.*
import java.io.StringWriter
import java.math.BigDecimal


    fun lagAvstemingsdata(
        aksjon: Aksjonsdata,
        total: Totaldata? = null,
        periode: Periodedata? = null,
        grunnlag: Grunnlagsdata? = null
    ): String {
        val avstemmingsdata = Avstemmingsdata(
            aksjon = aksjon,
            total = total,
            periode = periode,
            grunnlag = grunnlag
        )
    return serializeAvstemminsdata(avstemmingsdata)
    }

    private fun serializeAvstemminsdata(data: Avstemmingsdata): String {
        val serializer = Persister()
        var stringWriter = StringWriter()
        serializer.write(data, stringWriter)
        return stringWriter.buffer.replace(regex = Regex("&lt;"), "<").replace(regex = Regex("&gt;"), ">")
    }

    fun aksjon(avstemmingTom: String, type: AksjonType) =
        Aksjonsdata(
            aksjonType = type,
            kildeType = KildeType.MOTT,
            avstemmingType = AvstemmingType.GRSN,
            avleverendeKomponentKode = "OS",
            mottakendeKomponentKode = "OSS",
            underkomponentKode = "WHO_KNOWS",
            nokkelFom = "TJA_FRA",
            nokkelTom = "TJA_TIL",
            tidspunktAvstemmingTom = avstemmingTom,
            avleverendeAvstemmingId = "ENELLERANNENUNIKSAK",
            brukerId = "TJA_ID"
        )

    fun total(antall: Int, belop: BigDecimal) =
        Totaldata(
            totalAntall = antall,
            totalBelop = belop,
            fortegn = Fortegn.T
        )

    fun periode(fom: String, tom: String) =
        Periodedata(
            datoAvstemtFom = fom,
            datoAvstemtTom = tom
        )

    fun grunnlag(
        godkjentAntall: Int, godkjentBelop: BigDecimal,
        varselAntall: Int = 0, varselBelop: BigDecimal = BigDecimal(0),
        avvistAntall: Int = 0, avvistBelop: BigDecimal = BigDecimal(0),
        manglerAntall: Int = 0, manglerBelop: BigDecimal = BigDecimal(0)
    ) =
        Grunnlagsdata(
            godkjentAntall = godkjentAntall,
            godkjentBelop = godkjentBelop,
            godkjentFortegn = Fortegn.T,
            varselAntall = varselAntall,
            varselBelop = varselBelop,
            varselFortegn = Fortegn.T,
            avvistAntall = avvistAntall,
            avvistBelop = avvistBelop,
            avvistFortegn = Fortegn.T,
            manglerAntall = manglerAntall,
            manglerBelop = manglerBelop,
            manglerFortegn = Fortegn.T
        )

