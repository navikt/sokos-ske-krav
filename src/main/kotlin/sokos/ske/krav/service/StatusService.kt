package sokos.ske.krav.service

import io.ktor.client.call.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.datetime.Clock
import kotlinx.serialization.SerializationException
import mu.KotlinLogging
import sokos.ske.krav.client.SkeClient
import sokos.ske.krav.domain.ske.requests.Kravidentifikatortype
import sokos.ske.krav.domain.ske.responses.MottaksStatusResponse
import sokos.ske.krav.domain.ske.responses.ValideringsFeilResponse
import sokos.ske.krav.util.createKravidentifikatorPair


class StatusService(
    private val skeClient: SkeClient,
    private val databaseService: DatabaseService = DatabaseService()
) {

    private val logger = KotlinLogging.logger {}
    suspend fun hentOgOppdaterMottaksStatus(): List<String> {
        var antall = 0
        var feil = 0

        val start = Clock.System.now()
        val krav = databaseService.hentAlleKravSomIkkeErReskotrofort()
        println("antall krav som ikke er reskontroført: ${krav.size}")
        var tidSiste = Clock.System.now()
        val tidHentAlleKrav = (tidSiste - start).inWholeMilliseconds
        var tidHentMottakstatus = 0L
        var tidOppdaterstatus = 0L
        val result = krav.map {

            var kravIdentifikatorType = Kravidentifikatortype.SKATTEETATENSKRAVIDENTIFIKATOR
            var kravIdentifikator = it.saksnummerSKE

            if (it.saksnummerSKE.isEmpty()) {
                kravIdentifikator = it.referanseNummerGammelSak
                kravIdentifikatorType = Kravidentifikatortype.OPPDRAGSGIVERSKRAVIDENTIFIKATOR

                println("is empty, Kravident satt: $kravIdentifikator")
            }
            antall++
            val response = skeClient.getMottaksStatus(kravIdentifikator, kravIdentifikatorType)

            tidHentMottakstatus += (Clock.System.now() - tidSiste).inWholeMilliseconds
            tidSiste = Clock.System.now()

            if (response.status.isSuccess()) {
                try {
                    val mottaksstatus = response.body<MottaksStatusResponse>()
                    databaseService.updateStatus(mottaksstatus, it.corr_id)
                    tidOppdaterstatus += (Clock.System.now() - tidSiste).inWholeMilliseconds
                    tidSiste = Clock.System.now()
                } catch (e: SerializationException) {
                    feil++
                    logger.error("Feil i dekoding av MottaksStatusResponse: ${e.message}")
                    throw e
                } catch (e: IllegalArgumentException) {
                    feil++
                    logger.error("Response er ikke på forventet format for MottaksStatusResponse : ${e.message}")
                    throw e
                }
            } else {
                println(response.status)
            }
            "Status ok: ${response.status.value}, ${response.bodyAsText()}"
        }
        println("Antall krav hele greia: Antall behandlet  $antall, Antall feilet: $feil")
        println("tid for hele greia: ${(Clock.System.now() - start).inWholeMilliseconds}")
        println("Tid for å hente alle krav: ${tidHentAlleKrav}")
        println("Totalt tid for Henting av MOTTAKSTATUS: ${tidHentMottakstatus}")
        println("Totalt tid for Oppdatering av MOTTAKSTATUS: ${tidOppdaterstatus}")

        return result + "Antall behandlet  $antall, Antall feilet: $feil"
    }

    suspend fun hentValideringsfeil() {
        val krav = databaseService.getAlleKravMedValideringsfeil()

        krav.forEach {
            val kravidentifikatorPair = createKravidentifikatorPair(it)
            val response = skeClient.getValideringsfeil(kravidentifikatorPair.first, kravidentifikatorPair.second)

            if (response.status.isSuccess()) {
                val valideringsfeilResponse = response.body<ValideringsFeilResponse>()
                databaseService.saveValideringsfeil(valideringsfeilResponse, it.saksnummerSKE)
                "Status OK: ${response.bodyAsText()}"
            } else {
                "Status FAILED: ${response.status.value}, ${response.bodyAsText()}"
            }
        }

    }


}