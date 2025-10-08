package no.nav.sokos.ske.krav.service.integration

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode
import io.mockk.mockk

import no.nav.sokos.ske.krav.client.SkeClient
import no.nav.sokos.ske.krav.listener.DBListener
import no.nav.sokos.ske.krav.security.MaskinportenAccessTokenProvider
import no.nav.sokos.ske.krav.service.DatabaseService
import no.nav.sokos.ske.krav.service.OpprettKravService
import no.nav.sokos.ske.krav.util.MockHttpClientUtils
import no.nav.sokos.ske.krav.util.getAllKrav
import no.nav.sokos.ske.krav.util.setUpMockHttpClient

internal class OpprettKravServiceIntegrationTest :
    BehaviorSpec({
        val dbListener = DBListener()

        Given("2 Nye krav skal opprettes ") {
            dbListener.migrate("SQLscript/2NyeKrav.sql")

            val kravSomSkalSendes = dbListener.dataSource.connection.getAllKrav()
            kravSomSkalSendes.size shouldBe 2

            When("Response fra SKE er OK") {
                val kravidentifikatorSKE = "4321"
                val skeOKResponse = MockHttpClientUtils.Responses.nyttKravResponse(kravidentifikatorSKE)

                val httpClient = setUpMockHttpClient(listOf(MockHttpClientUtils.MockRequestObj(skeOKResponse, MockHttpClientUtils.EndepunktType.OPPRETT, HttpStatusCode.OK)))
                val skeClient = SkeClient(skeEndpoint = "", client = httpClient, tokenProvider = mockk<MaskinportenAccessTokenProvider>(relaxed = true))

                OpprettKravService(skeClient, DatabaseService(dbListener.dataSource)).sendAllOpprettKrav(kravSomSkalSendes)

                Then("Skal kravene oppdateres med SKE kravidentifikator") {
                    dbListener.dataSource.connection.getAllKrav().run {
                        size shouldBe 2
                        filter { it.saksnummerNAV == "1111-navsaksnr" }.size shouldBe 1
                        filter { it.saksnummerNAV == "2222-navsaksnr" }.size shouldBe 1
                        filter { it.kravidentifikatorSKE == kravidentifikatorSKE }.size shouldBe 2
                    }
                }
            }

            When("Response fra SKE ikke er OK") {
                val httpClient = setUpMockHttpClient(listOf(MockHttpClientUtils.MockRequestObj("", MockHttpClientUtils.EndepunktType.OPPRETT, HttpStatusCode.BadRequest)))
                val skeClient = SkeClient(skeEndpoint = "", client = httpClient, tokenProvider = mockk<MaskinportenAccessTokenProvider>(relaxed = true))

                OpprettKravService(skeClient, DatabaseService(dbListener.dataSource)).sendAllOpprettKrav(kravSomSkalSendes)

                Then("Skal kravene ikke oppdateres") {
                    dbListener.dataSource.connection.getAllKrav().run {
                        size shouldBe 2
                        filter { it.saksnummerNAV == "1111-navsaksnr" }.size shouldBe 1
                        filter { it.saksnummerNAV == "2222-navsaksnr" }.size shouldBe 1
                        filter { it.kravidentifikatorSKE == "" }.size shouldBe 2
                    }
                }
            }
        }
    })
