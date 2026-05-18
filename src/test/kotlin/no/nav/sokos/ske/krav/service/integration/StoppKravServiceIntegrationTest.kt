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
import no.nav.sokos.ske.krav.listener.DBListener
import no.nav.sokos.ske.krav.listener.DBListener.dataSource
import no.nav.sokos.ske.krav.listener.DBListener.kravRepository
import no.nav.sokos.ske.krav.security.MaskinportenAccessTokenProvider
import no.nav.sokos.ske.krav.service.STOPP_KRAV
import no.nav.sokos.ske.krav.service.StoppKravService
import no.nav.sokos.ske.krav.util.getAllKrav
import no.nav.sokos.ske.krav.util.http.Endpoint
import no.nav.sokos.ske.krav.util.http.MockHttpClient
import no.nav.sokos.ske.krav.util.http.MockResponse
import no.nav.sokos.ske.krav.util.http.MockResponsesBody.genericFeilResponse
import no.nav.sokos.ske.krav.util.transaction

class StoppKravServiceIntegrationTest :
    BehaviorSpec({
        extensions(DBListener)
        beforeEach { CircuitBreakerManager.circuitBreaker.reset() }

        Given("2 krav skal stoppes") {
            DBListener.clearDB()
            DBListener.loadInitScripts("SQLscript/krav/TiNyeKrav.sql", "SQLscript/krav/ToStoppedeKrav.sql")

            val kravSomSkalSendes = kravRepository.getAllUnsentKrav()
            kravSomSkalSendes.shouldHaveSize(2)
            kravSomSkalSendes.count { it.kravtype == STOPP_KRAV } shouldBe 2

            When("Response fra SKE trigger circuit breaker") {
                val avskrivingResponse = MockResponse(Endpoint.AVSKRIVING, genericFeilResponse(), HttpStatusCode.Forbidden)

                val httpClient = MockHttpClient.client(avskrivingResponse)
                val skeClient = SkeClient(skeEndpoint = "", client = httpClient, tokenProvider = mockk<MaskinportenAccessTokenProvider>(relaxed = true))

                val stoppKravServiceSpy = spyk(StoppKravService(skeClient), recordPrivateCalls = true)

                val requestResults = stoppKravServiceSpy.sendAllStoppKrav(kravSomSkalSendes)
                Then("Skal det være 0 requestResults") {
                    requestResults.shouldBeEmpty()
                }
                And("sendStoppKrav skal kalles kun én gang") {
                    coVerify(exactly = 1) {
                        stoppKravServiceSpy["sendStoppKrav"](ofType<Krav>())
                    }
                }
                Then("Skal krav ikke oppdateres med status sendt") {
                    val allKrav =
                        dataSource.transaction { session ->
                            kravRepository.getAllKrav(session)
                        }

                    allKrav
                        .filter { it.status == Status.KRAV_IKKE_SENDT }
                        .shouldHaveSize(2)

                    kravRepository.getAllUnsentKrav().shouldHaveSize(2)
                }
            }
        }
    })
