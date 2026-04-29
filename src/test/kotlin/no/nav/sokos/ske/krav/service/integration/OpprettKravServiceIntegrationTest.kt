package no.nav.sokos.ske.krav.service.integration

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode
import io.mockk.coVerify
import io.mockk.spyk

import no.nav.sokos.ske.krav.config.CircuitBreakerManager
import no.nav.sokos.ske.krav.domain.Krav
import no.nav.sokos.ske.krav.listener.DBListener
import no.nav.sokos.ske.krav.service.DatabaseService
import no.nav.sokos.ske.krav.service.OpprettKravService
import no.nav.sokos.ske.krav.util.getAllKrav
import no.nav.sokos.ske.krav.util.http.Endpoint
import no.nav.sokos.ske.krav.util.http.MockResponsesBody.genericFeilResponse
import no.nav.sokos.ske.krav.util.http.MockResponsesBody.nyttKravResponse
import no.nav.sokos.ske.krav.util.skeClient

internal class OpprettKravServiceIntegrationTest :
    BehaviorSpec({
        extensions(DBListener)
        beforeEach { CircuitBreakerManager.circuitBreaker.reset() }

        val dbService = DatabaseService(DBListener.dataSource)

        Given("2 Nye krav skal opprettes ") {
            DBListener.clearDB()
            DBListener.loadInitScript("SQLscript/krav/ToNyeKrav.sql")

            val kravSomSkalSendes = dbService.getAllUnsentKrav()
            kravSomSkalSendes.size shouldBe 2

            When("Response fra SKE  trigger circuit breaker") {
                val skeClient = skeClient(Endpoint.OPPRETT.responding(genericFeilResponse(), HttpStatusCode.InternalServerError))

                val opprettKravServiceSpy = spyk(OpprettKravService(skeClient, DatabaseService(DBListener.dataSource)), recordPrivateCalls = true)
                val requestResults = opprettKravServiceSpy.sendAllOpprettKrav(kravSomSkalSendes)

                Then("Skal sendOpprettKrav kalles kun én gang") {
                    coVerify(exactly = 1) { opprettKravServiceSpy["sendOpprettKrav"](ofType<Krav>()) }
                }
                Then("Skal kravene ikke oppdateres") {
                    val krav =
                        DBListener.dataSource.connection.use { con ->
                            con.getAllKrav()
                        }
                    krav.size shouldBe 2
                    krav.count { it.saksnummerNAV == "1111-navsaksnr" } shouldBe 1
                    krav.count { it.saksnummerNAV == "2222-navsaksnr" } shouldBe 1
                    krav.count { it.kravidentifikatorSKE.isBlank() } shouldBe 2
                    dbService.getAllUnsentKrav().size shouldBe 2
                }

                And("Det skal være ingen requestresults") {
                    requestResults.size shouldBe 0
                }
            }

            When("Response fra SKE er OK") {
                CircuitBreakerManager.circuitBreaker.reset()
                val kravidentifikatorSKE = "4321"

                val skeClient = skeClient(Endpoint.OPPRETT.responding(nyttKravResponse(kravidentifikatorSKE)))

                val opprettKravServiceSpy = spyk(OpprettKravService(skeClient, DatabaseService(DBListener.dataSource)), recordPrivateCalls = true)
                val requestResults = opprettKravServiceSpy.sendAllOpprettKrav(kravSomSkalSendes)

                Then("Skal sendOpprettKrav kalles to ganger") {
                    coVerify(exactly = 2) { opprettKravServiceSpy["sendOpprettKrav"](ofType<Krav>()) }
                }
                Then("Skal kravene oppdateres med SKE kravidentifikator") {
                    val krav =
                        DBListener.dataSource.connection.use { con ->
                            con.getAllKrav()
                        }
                    krav.size shouldBe 2
                    krav.count { it.saksnummerNAV == "1111-navsaksnr" } shouldBe 1
                    krav.count { it.saksnummerNAV == "2222-navsaksnr" } shouldBe 1
                    krav.count { it.kravidentifikatorSKE == kravidentifikatorSKE } shouldBe 2
                    dbService.getAllUnsentKrav().size shouldBe 0
                }
                And("Det skal være to requestResults") {
                    requestResults.size shouldBe 2
                }
            }
        }
    })
