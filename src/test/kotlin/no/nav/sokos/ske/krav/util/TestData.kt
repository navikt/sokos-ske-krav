package no.nav.sokos.ske.krav.util

import java.math.BigDecimal
import java.time.LocalDate

import no.nav.sokos.ske.krav.domain.Status
import no.nav.sokos.ske.krav.dto.nav.KravLinje

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

    fun avstemmingReponse(kravIdentifikator: String = "1234") = """{"kravidentifikator": "$kravIdentifikator"}"""

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

    fun getKravlinjerTestData(): MutableList<KravLinje> {
        val okLinje =
            KravLinje(
                linjenummer = 1,
                saksnummerNav = "saksnummer",
                belop = BigDecimal.ONE,
                vedtaksDato = LocalDate.now(),
                gjelderId = "gjelderID",
                periodeFOM = "20231201",
                periodeTOM = "20231212",
                kravKode = "KS KS",
                referansenummerGammelSak = "refgammelsak",
                transaksjonsDato = "20230112",
                enhetBosted = "bost",
                enhetBehandlende = "beh",
                kodeHjemmel = "T",
                kodeArsak = "arsak",
                belopRente = BigDecimal.ONE,
                fremtidigYtelse = BigDecimal.ONE,
                utbetalDato = LocalDate.now().minusDays(1),
                fagsystemId = "1234",
            )
        return mutableListOf(
            okLinje,
            okLinje.copy(linjenummer = 2, saksnummerNav = "saksnummer2"),
            okLinje.copy(linjenummer = 3, saksnummerNav = "saksnummer3"),
            okLinje.copy(linjenummer = 4, saksnummerNav = "saksnummer4"),
            okLinje.copy(linjenummer = 5, saksnummerNav = "saksnummer5"),
        )
    }
}
