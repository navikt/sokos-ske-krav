package no.nav.sokos.ske.krav.util

import no.nav.sokos.ske.krav.domain.Status

object TestData {
    //language=json
    fun mottaksStatusResponse(
        kravIdentifikator: String = "1234",
        status: String = Status.RESKONTROFOERT.value,
    ): String =
        """
        {
             "kravidentifikator": "$kravIdentifikator",
             "oppdragsgiversKravidentifikator": "4321",
             "mottaksstatus": "$status",
             "statusOppdatert": "2023-10-04T04:47:08.482Z"
        }
        """.trimIndent()

    fun nyttKravResponse(kravIdentifikator: String = "1234") = """{"kravidentifikator": "$kravIdentifikator"}"""

    fun nyEndringResponse(transaksjonsId: String = "791e5955-af86-42fe-b609-d4fc2754e35e") = """{"transaksjonsid": "$transaksjonsId"}"""

    //language=json
    fun innkrevingsOppdragEksistererIkkeResponse(kravIdentifikator: String = "1234") =
        """      
        {
            "type":"tag:skatteetaten.no,2024:innkreving:innkrevingsoppdrag:innkrevingsoppdrag-eksisterer-ikke",
            "title":"Innkrevingsoppdrag eksisterer ikke",
            "status":404,
            "detail":"Innkrevingsoppdrag med oppdragsgiversKravidentifikator=$kravIdentifikator eksisterer ikke",
            "instance":"/api/innkreving/innkrevingsoppdrag/v1/innkrevingsoppdrag/avskriving"
        }
        """.trimIndent()

    //language=json
    fun valideringsfeilResponse(
        error: String,
        message: String,
    ) = """
        {
            "valideringsfeil": [{
              "error":   "$error",
              "message": "$message"
            }]
        }
        """.trimMargin()

    //language=json
    fun emptyValideringsfeilResponse() =
        """
        {
            "valideringsfeil": []
        }
        """.trimMargin()

    //language=json
    fun feilResponse() =
        """
        {
            "type": "error-type",
            "title": "Error Title",
            "status": 404,
            "detail": "Error detail message",
            "instance": "/test/error"
        }
        """.trimIndent()
}
