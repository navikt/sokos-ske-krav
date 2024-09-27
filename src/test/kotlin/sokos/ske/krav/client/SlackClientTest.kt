package sokos.ske.krav.client

import io.kotest.core.spec.style.FunSpec

class SlackClientTest : FunSpec({

    test("doPost").config(enabled = false) {

        val sk = SlackClient()
        sk.doPost("API-POST", "hjallafil", "Egentlig ikke noe galt bare første implementering")
    }
})
