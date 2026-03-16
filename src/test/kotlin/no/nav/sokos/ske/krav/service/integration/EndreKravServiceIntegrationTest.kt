package no.nav.sokos.ske.krav.service.integration

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.spyk

import no.nav.sokos.ske.krav.client.SkeClient
import no.nav.sokos.ske.krav.config.CircuitBreakerManager
import no.nav.sokos.ske.krav.domain.Krav
import no.nav.sokos.ske.krav.domain.Status
import no.nav.sokos.ske.krav.dto.ske.requests.KravidentifikatorType
import no.nav.sokos.ske.krav.listener.DBListener
import no.nav.sokos.ske.krav.security.MaskinportenAccessTokenProvider
import no.nav.sokos.ske.krav.service.DatabaseService
import no.nav.sokos.ske.krav.service.ENDRING_HOVEDSTOL
import no.nav.sokos.ske.krav.service.ENDRING_RENTE
import no.nav.sokos.ske.krav.service.EndreKravService
import no.nav.sokos.ske.krav.util.MockHttpClientUtils
import no.nav.sokos.ske.krav.util.MockHttpClientUtils.Responses
import no.nav.sokos.ske.krav.util.getAllKrav
import no.nav.sokos.ske.krav.util.setUpMockHttpClient

class EndreKravServiceIntegrationTest :
    BehaviorSpec({
        extensions(DBListener)
        beforeEach { CircuitBreakerManager.circuitBreaker.reset() }
        val dbService = DatabaseService(DBListener.dataSource)

        Given("2 krav skal endres") {
            DBListener.clearDB()
            DBListener.loadInitScript("SQLscript/krav/TiNyeKrav.sql")
            DBListener.loadInitScript("SQLscript/krav/ToEndredeKrav.sql")

            val kravSomSkalSendes = dbService.getAllUnsentKrav()
            kravSomSkalSendes.size shouldBe 4
            kravSomSkalSendes.count { it.kravtype == ENDRING_RENTE || it.kravtype == ENDRING_HOVEDSTOL } shouldBe 4

            When("Response fra SKE trigger circuit breaker") {
                val endreRenterKall = MockHttpClientUtils.MockRequestObj(Responses.genericFeilResponse(), MockHttpClientUtils.EndepunktType.ENDRE_RENTER, HttpStatusCode.Forbidden)
                val endreHovedstolKall = MockHttpClientUtils.MockRequestObj(Responses.genericFeilResponse(), MockHttpClientUtils.EndepunktType.ENDRE_HOVEDSTOL, HttpStatusCode.Forbidden)
                val httpClient =
                    setUpMockHttpClient(listOf(endreRenterKall, endreHovedstolKall))
                val skeClient = SkeClient(skeEndpoint = "", client = httpClient, tokenProvider = mockk<MaskinportenAccessTokenProvider>(relaxed = true))

                val endreKravServiceSpy = spyk(EndreKravService(skeClient, dbService), recordPrivateCalls = true)
                val requestResults = endreKravServiceSpy.sendAllEndreKrav(kravSomSkalSendes)

                Then("Skal sendEndreKrav kalles kun én gang") {
                    coVerify(exactly = 1) { endreKravServiceSpy["sendEndreKrav"](ofType<String>(), ofType<KravidentifikatorType>(), ofType<Krav>()) }
                }
                And("Det skal være 0 requestresults") {
                    requestResults.size shouldBe 0
                }
                Then("Skal kravstatus ikke oppdateres") {
                    val krav =
                        DBListener.dataSource.connection.use { con ->
                            con.getAllKrav()
                        }

                    dbService.getAllUnsentKrav().size shouldBe 4
                    krav.filter { it.status == Status.KRAV_SENDT.value }.size shouldBe 0
                    krav.filter { it.status == Status.KRAV_IKKE_SENDT.value }.size shouldBe 4
                }
            }
            When("Response fra SKE er OK") {
                CircuitBreakerManager.circuitBreaker.reset()
                val endreRenterKall = MockHttpClientUtils.MockRequestObj(Responses.nyEndringResponse(), MockHttpClientUtils.EndepunktType.ENDRE_RENTER, HttpStatusCode.OK)
                val endreHovedstolKall = MockHttpClientUtils.MockRequestObj(Responses.nyEndringResponse(), MockHttpClientUtils.EndepunktType.ENDRE_HOVEDSTOL, HttpStatusCode.OK)
                val httpClient =
                    setUpMockHttpClient(listOf(endreRenterKall, endreHovedstolKall))
                val skeClient = SkeClient(skeEndpoint = "", client = httpClient, tokenProvider = mockk<MaskinportenAccessTokenProvider>(relaxed = true))

                EndreKravService(skeClient, dbService).sendAllEndreKrav(kravSomSkalSendes)
                Then("Skal krav oppdateres med status sendt") {
                    DBListener.dataSource.connection.use { con ->
                        con.getAllKrav().filter { it.status == Status.KRAV_SENDT.value }.size shouldBe 4
                    }
                    dbService.getAllUnsentKrav().size shouldBe 0
                }
            }
        }
    })
