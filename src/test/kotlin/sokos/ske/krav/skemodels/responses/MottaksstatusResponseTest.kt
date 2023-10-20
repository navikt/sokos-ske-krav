package sokos.ske.krav.skemodels.responses

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.fail
import sokos.ske.krav.skemodels.responses.MottaksstatusResponse.MottaksStatus.*

class MottaksstatusResponseTest : FunSpec({

    test("Tester at alle mottaksstatuser har riktig verdi") {

        MottaksstatusResponse.MottaksStatus.values().forEach {
            when (it){
                MOTTATTUNDERBEHANDLING -> it.value shouldBe "MOTTATT_UNDER_BEHANDLING"
                VALIDERINGSFEIL -> it.value shouldBe "VALIDERINGSFEIL"
                RESKONTROFOERT -> it.value shouldBe "RESKONTROFOERT"
                else -> fail {   "Mottakststaus har en verdi som ikke finnes i test" }

            }
        }
    }

})

