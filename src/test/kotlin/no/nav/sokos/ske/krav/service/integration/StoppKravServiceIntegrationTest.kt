package no.nav.sokos.ske.krav.service.integration

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode
import io.mockk.coVerify
import io.mockk.spyk

import no.nav.sokos.ske.krav.config.CircuitBreakerManager
import no.nav.sokos.ske.krav.domain.Krav
import no.nav.sokos.ske.krav.domain.Status
import no.nav.sokos.ske.krav.listener.DBListener
import no.nav.sokos.ske.krav.service.DatabaseService
import no.nav.sokos.ske.krav.service.STOPP_KRAV
import no.nav.sokos.ske.krav.service.StoppKravService
import no.nav.sokos.ske.krav.util.getAllKrav
import no.nav.sokos.ske.krav.util.http.Endpoint
import no.nav.sokos.ske.krav.util.http.MockResponsesBody.avskrivKravResponse
import no.nav.sokos.ske.krav.util.http.MockResponsesBody.genericFeilResponse
import no.nav.sokos.ske.krav.util.skeClient

class StoppKravServiceIntegrationTest :
    BehaviorSpec({
        extensions(DBListener)
        beforeEach { CircuitBreakerManager.circuitBreaker.reset() }
        val dbService = DatabaseService(DBListener.dataSource)

        Given("2 krav skal stoppes") {
            DBListener.clearDB()
            DBListener.loadInitScript("SQLscript/krav/TiNyeKrav.sql")
            DBListener.loadInitScript("SQLscript/krav/ToStoppedeKrav.sql")

            val kravSomSkalSendes = dbService.getAllUnsentKrav()
            kravSomSkalSendes.size shouldBe 2
            kravSomSkalSendes.count { it.kravtype == STOPP_KRAV } shouldBe 2

            When("Response fra SKE trigger circuit breaker") {
                val skeClient = skeClient(Endpoint.AVSKRIVING.responding(genericFeilResponse(), HttpStatusCode.Forbidden))

                val stoppKravServiceSpy = spyk(StoppKravService(skeClient, dbService), recordPrivateCalls = true)

                val requestResults = stoppKravServiceSpy.sendAllStoppKrav(kravSomSkalSendes)
                Then("Skal det være 0 requestResults") {
                    requestResults.size shouldBe 0
                }
                And("sendStoppKrav skal kalles kun én gang") {
                    coVerify(exactly = 1) {
                        stoppKravServiceSpy["sendStoppKrav"](ofType<Krav>())
                    }
                }
                Then("Skal krav ikke oppdateres med status sendt") {
                    DBListener.dataSource.connection.use { con ->
                        con.getAllKrav().filter { it.status == Status.KRAV_IKKE_SENDT.value }.size shouldBe 2
                    }
                    dbService.getAllUnsentKrav().size shouldBe 2
                }
            }
            When("Response fra SKE er OK") {
                CircuitBreakerManager.circuitBreaker.reset()
                val skeClient = skeClient(Endpoint.AVSKRIVING.responding(avskrivKravResponse()))

                StoppKravService(skeClient, dbService).sendAllStoppKrav(kravSomSkalSendes)

                Then("Skal krav oppdateres med status sendt") {
                    val krav =
                        DBListener.dataSource.connection.use { con ->
                            con.getAllKrav()
                        }
                    krav.count { it.status == Status.KRAV_SENDT.value } shouldBe 2
                    dbService.getAllUnsentKrav().size shouldBe 0
                }
            }
        }
    })
