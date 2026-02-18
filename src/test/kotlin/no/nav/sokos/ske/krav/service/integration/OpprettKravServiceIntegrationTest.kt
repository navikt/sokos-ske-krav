package no.nav.sokos.ske.krav.service.integration

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode
import io.mockk.mockk

import no.nav.sokos.ske.krav.client.SkeClient
import no.nav.sokos.ske.krav.config.CircuitBreakerManager
import no.nav.sokos.ske.krav.listener.DBListener
import no.nav.sokos.ske.krav.security.MaskinportenAccessTokenProvider
import no.nav.sokos.ske.krav.service.DatabaseService
import no.nav.sokos.ske.krav.service.OpprettKravService
import no.nav.sokos.ske.krav.util.MockHttpClientUtils
import no.nav.sokos.ske.krav.util.MockHttpClientUtils.Responses
import no.nav.sokos.ske.krav.util.getAllKrav
import no.nav.sokos.ske.krav.util.setUpMockHttpClient

internal class OpprettKravServiceIntegrationTest :
    BehaviorSpec({
        extensions(DBListener)
        beforeEach { CircuitBreakerManager.circuitBreaker.reset() }

        val dbService = DatabaseService(DBListener.dataSource)

        Given("2 Nye krav skal opprettes ") {
            DBListener.clearDB()
            DBListener.loadInitScript("SQLscript/2NyeKrav.sql")

            val kravSomSkalSendes = dbService.getAllUnsentKrav()
            kravSomSkalSendes.size shouldBe 2

            When("Response fra SKE  trigger circuit breaker") {
                val httpClient =
                    setUpMockHttpClient(listOf(MockHttpClientUtils.MockRequestObj(Responses.genericFeilResponse(), MockHttpClientUtils.EndepunktType.OPPRETT, HttpStatusCode.InternalServerError)))
                val skeClient = SkeClient(skeEndpoint = "", client = httpClient, tokenProvider = mockk<MaskinportenAccessTokenProvider>(relaxed = true))

                OpprettKravService(skeClient, DatabaseService(DBListener.dataSource)).sendAllOpprettKrav(kravSomSkalSendes)

                Then("Skal kravene ikke oppdateres") {
                    val krav =
                        DBListener.dataSource.connection.use { con ->
                            con.getAllKrav()
                        }
                    krav.size shouldBe 2
                    krav.count { it.saksnummerNAV == "1111-navsaksnr" } shouldBe 1
                    krav.count { it.saksnummerNAV == "2222-navsaksnr" } shouldBe 1
                    krav.count { it.kravidentifikatorSKE.isBlank() } shouldBe 2
                }

                dbService.getAllUnsentKrav().size shouldBe 2
            }

            When("Response fra SKE er OK") {
                CircuitBreakerManager.circuitBreaker.reset()
                val kravidentifikatorSKE = "4321"
                val skeOKResponse = Responses.nyttKravResponse(kravidentifikatorSKE)

                val httpClient = setUpMockHttpClient(listOf(MockHttpClientUtils.MockRequestObj(skeOKResponse, MockHttpClientUtils.EndepunktType.OPPRETT, HttpStatusCode.OK)))
                val skeClient = SkeClient(skeEndpoint = "", client = httpClient, tokenProvider = mockk<MaskinportenAccessTokenProvider>(relaxed = true))

                OpprettKravService(skeClient, DatabaseService(DBListener.dataSource)).sendAllOpprettKrav(kravSomSkalSendes)

                Then("Skal kravene oppdateres med SKE kravidentifikator") {
                    val krav =
                        DBListener.dataSource.connection.use { con ->
                            con.getAllKrav()
                        }
                    krav.size shouldBe 2
                    krav.count { it.saksnummerNAV == "1111-navsaksnr" } shouldBe 1
                    krav.count { it.saksnummerNAV == "2222-navsaksnr" } shouldBe 1
                    krav.count { it.kravidentifikatorSKE == kravidentifikatorSKE } shouldBe 2
                }

                dbService.getAllUnsentKrav().size shouldBe 0
            }
        }
    })
