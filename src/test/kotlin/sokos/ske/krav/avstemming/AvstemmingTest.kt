package sokos.ske.krav.avstemming

import io.kotest.core.spec.style.FunSpec
import sokos.ske.krav.avstemming.kontrakt.AksjonType
import java.math.BigDecimal

class AvstemmingTest : FunSpec({

    test("lag Avstemingsdata med dummydata") {
     println(
      lagAvstemingsdata(
       aksjon(avstemmingTom = "010101", AksjonType.START, ),
       total(10, BigDecimal(123.45)),
       periode("01012023", "31012023"),
       grunnlag(godkjentAntall = 10, godkjentBelop = BigDecimal(123.45))
      )
     )

    }


})
