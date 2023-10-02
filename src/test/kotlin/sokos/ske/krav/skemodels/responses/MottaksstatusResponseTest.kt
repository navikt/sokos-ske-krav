package sokos.ske.krav.skemodels.responses

import io.kotest.core.spec.style.FunSpec

class MottaksstatusResponseTest : FunSpec({

    test("hei") {

        val msr = MottaksstatusResponse.Mottaksstatus.MOTTATTUNDERBEHANDLING
        println( "value: ${msr.value}, Name: ${msr.name}" )
    }

})

