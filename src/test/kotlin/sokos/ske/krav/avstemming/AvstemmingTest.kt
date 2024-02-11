package sokos.ske.krav.avstemming

import io.kotest.core.spec.style.FunSpec
import java.math.BigDecimal

class AvstemmingTest : FunSpec({

    test("lag Avstemingsdata med dummydata") {
     println(
      lagAvstemingsdata(
       aksjon("010101"),
       total(10, BigDecimal(123.45)),
       periode("01012023", "31012023"),
       grunnlag(godkjentAntall = 10, godkjentBelop = BigDecimal(123.45))
      )
     )

    }


})
