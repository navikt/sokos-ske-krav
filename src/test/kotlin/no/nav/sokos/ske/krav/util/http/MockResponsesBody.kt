package no.nav.sokos.ske.krav.util.http

import no.nav.sokos.ske.krav.domain.Status

object MockResponsesBody {
    //language=json
    val httpErrorResponse =
        """
        {
            "status": 403,
            "error": "Forbidden",
            "message": "You do not have permission to access this resource. Please check your API token or authentication details."
        }
        """.trimMargin()

    fun mottaksStatusResponse(
        kravIdentifikator: String = "1234",
        status: String = Status.RESKONTROFOERT.value,
    ) = //language=json
        """
        {
             "kravidentifikator": "$kravIdentifikator",
             "oppdragsgiversKravidentifikator": "4321",
             "mottaksstatus": "$status",
             "statusOppdatert": "2023-10-04T04:47:08.482Z"
         }
        """.trimIndent()

    fun nyttKravResponse(kravIdentifikator: String = "1234") = """{"kravidentifikator": "$kravIdentifikator"}"""

    fun avstemmingResponse(kravIdentifikator: String = "1234") = """{"kravidentifikator": "$kravIdentifikator"}"""

    fun nyEndringResponse(transaksjonsId: String = "791e5955-af86-42fe-b609-d4fc2754e35e") = """{"transaksjonsid": "$transaksjonsId"}"""

    fun avskrivKravResponse(transaksjonsId: String = "791e5955-af86-42fe-b609-d4fc2754e35e") = """{"transaksjonsid": "$transaksjonsId"}"""

    fun innkrevingsOppdragEksistererIkkeResponse(kravIdentifikator: String = "1234") =
        //language=json
        """      
        {
            "type":"tag:skatteetaten.no,2024:innkreving:innkrevingsoppdrag:innkrevingsoppdrag-eksisterer-ikke",
            "title":"Innkrevingsoppdrag eksisterer ikke",
            "status":404,
            "detail":"Innkrevingsoppdrag med oppdragsgiversKravidentifikator=$kravIdentifikator eksisterer ikke",
            "instance":"/api/innkreving/innkrevingsoppdrag/v1/innkrevingsoppdrag/avskriving"
        }
        """.trimIndent()

    fun genericFeilResponse(kravIdentifikator: String = "1234") =
        //language=json
        """      
        {
            "type":"tag:skatteetaten.no,2024:innkreving:innkrevingsoppdrag:innkrevingsoppdrag-eksisterer-ikke",
            "title":"Innkrevingsoppdrag eksisterer ikke",
            "status":422,
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
}
