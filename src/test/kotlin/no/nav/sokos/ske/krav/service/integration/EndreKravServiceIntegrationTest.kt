package no.nav.sokos.ske.krav.service.integration

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
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
import no.nav.sokos.ske.krav.listener.DBListener.kravRepository
import no.nav.sokos.ske.krav.security.MaskinportenAccessTokenProvider
import no.nav.sokos.ske.krav.service.ENDRING_HOVEDSTOL
import no.nav.sokos.ske.krav.service.ENDRING_RENTE
import no.nav.sokos.ske.krav.service.EndreKravService
import no.nav.sokos.ske.krav.util.getAllKrav
import no.nav.sokos.ske.krav.util.http.Endpoint
import no.nav.sokos.ske.krav.util.http.MockHttpClient
import no.nav.sokos.ske.krav.util.http.MockResponse
import no.nav.sokos.ske.krav.util.http.MockResponsesBody.genericFeilResponse

class EndreKravServiceIntegrationTest :
    BehaviorSpec({
        extensions(DBListener)
        beforeEach { CircuitBreakerManager.circuitBreaker.reset() }

        Given("2 krav skal endres") {
            DBListener.clearDB()
            DBListener.loadInitScripts("SQLscript/krav/TiNyeKrav.sql", "SQLscript/krav/ToEndredeKrav.sql")

            val kravSomSkalSendes = kravRepository.getAllUnsentKrav()
            kravSomSkalSendes.shouldHaveSize(4)
            kravSomSkalSendes.count { it.kravtype == ENDRING_RENTE || it.kravtype == ENDRING_HOVEDSTOL } shouldBe 4

            When("Response fra SKE trigger circuit breaker") {
                val endreRenterResponse = MockResponse(Endpoint.ENDRE_RENTER, genericFeilResponse(), HttpStatusCode.Forbidden)
                val endreHovedstolResponse = MockResponse(Endpoint.ENDRE_HOVEDSTOL, genericFeilResponse(), HttpStatusCode.Forbidden)

                val httpClient = MockHttpClient.client(endreRenterResponse, endreHovedstolResponse)

                val skeClient = SkeClient(skeEndpoint = "", client = httpClient, tokenProvider = mockk<MaskinportenAccessTokenProvider>(relaxed = true))
                val endreKravServiceSpy = spyk(EndreKravService(skeClient), recordPrivateCalls = true)
                val requestResults = endreKravServiceSpy.sendAllEndreKrav(kravSomSkalSendes)

                Then("Skal sendEndreKrav kalles kun én gang") {
                    coVerify(exactly = 1) { endreKravServiceSpy["sendEndreKrav"](ofType<String>(), ofType<KravidentifikatorType>(), ofType<Krav>()) }
                }
                And("Det skal være 0 requestresults") {
                    requestResults.shouldBeEmpty()
                }
                Then("Skal kravstatus ikke oppdateres") {
                    val krav = kravRepository.getAllKrav()

                    kravRepository.getAllUnsentKrav().shouldHaveSize(4)
                    krav.filter { it.status == Status.KRAV_SENDT }.shouldBeEmpty()
                    krav.filter { it.status == Status.KRAV_IKKE_SENDT }.shouldHaveSize(4)
                }
            }
        }
    })
