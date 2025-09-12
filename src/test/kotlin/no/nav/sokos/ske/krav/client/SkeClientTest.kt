package no.nav.sokos.ske.krav.client

import java.util.UUID

import kotlinx.datetime.LocalDate

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.common.ContentTypes
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode

import no.nav.sokos.ske.krav.domain.StonadsType
import no.nav.sokos.ske.krav.dto.ske.requests.AvskrivingRequest
import no.nav.sokos.ske.krav.dto.ske.requests.EndreRenteBeloepRequest
import no.nav.sokos.ske.krav.dto.ske.requests.HovedstolBeloep
import no.nav.sokos.ske.krav.dto.ske.requests.KravidentifikatorType
import no.nav.sokos.ske.krav.dto.ske.requests.OpprettInnkrevingsoppdragRequest
import no.nav.sokos.ske.krav.dto.ske.requests.RenteBeloep
import no.nav.sokos.ske.krav.dto.ske.requests.Skyldner
import no.nav.sokos.ske.krav.listener.WiremockListener

class SkeClientTest :
    FunSpec({
        extensions(listOf(WiremockListener))

        val skeClient: SkeClient by lazy {
            SkeClient(
                tokenProvider = WiremockListener.mockTokenClient,
                skeEndpoint = WiremockListener.wiremock.baseUrl() + "/",
            )
        }

        test("opprettKrav skal sende POST request med korrekt headers og body") {
            val request =
                OpprettInnkrevingsoppdragRequest(
                    stonadstype = StonadsType.TILBAKEKREVING_BARNETRYGD,
                    skyldner =
                        Skyldner(
                            identifikatorType = Skyldner.IdentifikatorType.PERSON,
                            identifikator = "12345678901",
                        ),
                    hovedstol = HovedstolBeloep(beloep = 1000),
                    renteBeloep = null,
                    oppdragsgiversReferanse = "REF123",
                    oppdragsgiversKravIdentifikator = "KRAV123",
                    fastsettelsesDato = LocalDate(2024, 1, 1),
                )
            val corrId = UUID.randomUUID().toString()

            WiremockListener.wiremock.stubFor(
                WireMock
                    .post(urlEqualTo("/$OPPRETT_KRAV_URL"))
                    .willReturn(
                        aResponse()
                            .withHeader(HttpHeaders.ContentType, ContentTypes.APPLICATION_JSON)
                            .withStatus(HttpStatusCode.Created.value),
                    ),
            )

            val response = skeClient.opprettKrav(request, corrId)
            response.status shouldBe HttpStatusCode.Created
        }

        test("endreRenter skal sende PUT request med korrekt headers og body") {
            val request =
                EndreRenteBeloepRequest(
                    renter =
                        listOf(
                            RenteBeloep(
                                beloep = 100,
                                renterIlagtDato = LocalDate(2024, 1, 1),
                            ),
                        ),
                )
            val kravidentifikator = "123"
            val corrId = UUID.randomUUID().toString()

            val url = String.format(ENDRE_RENTER_URL, kravidentifikator, KravidentifikatorType.OPPDRAGSGIVERSKRAVIDENTIFIKATOR.value, request, kravidentifikator, corrId)
            WiremockListener.wiremock.stubFor(
                WireMock
                    .put(urlEqualTo("/$url"))
                    .willReturn(
                        aResponse()
                            .withHeader(HttpHeaders.ContentType, ContentTypes.APPLICATION_JSON)
                            .withStatus(HttpStatusCode.OK.value),
                    ),
            )

            val response =
                skeClient.endreRenter(
                    request = request,
                    kravidentifikator = kravidentifikator,
                    kravidentifikatorType = KravidentifikatorType.OPPDRAGSGIVERSKRAVIDENTIFIKATOR,
                    corrID = corrId,
                )
            response.status shouldBe HttpStatusCode.OK
        }

        test("getMottaksStatus skal sende GET request  med korrekt headers og body") {
            val kravid = "123"

            val url = String.format(MOTTAKSSTATUS_URL, kravid, KravidentifikatorType.OPPDRAGSGIVERSKRAVIDENTIFIKATOR.value)
            WiremockListener.wiremock.stubFor(
                WireMock
                    .get(urlEqualTo("/$url"))
                    .willReturn(
                        aResponse()
                            .withHeader(HttpHeaders.ContentType, ContentTypes.APPLICATION_JSON)
                            .withStatus(HttpStatusCode.OK.value),
                    ),
            )
            val response =
                skeClient.getMottaksStatus(
                    kravid = kravid,
                    kravidentifikatorType = KravidentifikatorType.OPPDRAGSGIVERSKRAVIDENTIFIKATOR,
                )
            response.status shouldBe HttpStatusCode.OK
        }

        test("stoppKrav skal sende POST request  med korrekt headers og body") {
            val request =
                AvskrivingRequest(
                    kravidentifikatorType = KravidentifikatorType.OPPDRAGSGIVERSKRAVIDENTIFIKATOR.value,
                    kravidentifikator = "123",
                )
            val corrId = UUID.randomUUID().toString()

            WiremockListener.wiremock.stubFor(
                WireMock
                    .post(urlEqualTo("/$STOPP_KRAV_URL"))
                    .willReturn(
                        aResponse()
                            .withHeader(HttpHeaders.ContentType, ContentTypes.APPLICATION_JSON)
                            .withStatus(HttpStatusCode.OK.value),
                    ),
            )
            val response = skeClient.stoppKrav(request, corrId)
            response.status shouldBe HttpStatusCode.OK
        }

        test("getValideringsfeil skal sende GET request med korrekt headers") {
            val kravid = "123"

            val url = String.format(VALIDERINGSFEIL_URL, kravid, KravidentifikatorType.OPPDRAGSGIVERSKRAVIDENTIFIKATOR.value)
            WiremockListener.wiremock.stubFor(
                WireMock
                    .get(urlEqualTo("/$url"))
                    .willReturn(
                        aResponse()
                            .withHeader(HttpHeaders.ContentType, ContentTypes.APPLICATION_JSON)
                            .withStatus(HttpStatusCode.OK.value),
                    ),
            )

            val response =
                skeClient.getValideringsfeil(
                    kravid = kravid,
                    kravidentifikatorType = KravidentifikatorType.OPPDRAGSGIVERSKRAVIDENTIFIKATOR,
                )
            response.status shouldBe HttpStatusCode.OK
        }

        test("getSkeKravidentifikator skal sende GET requestmed korrekt headers") {
            val referanse = "123"

            val url = String.format(HENT_SKE_KRAVIDENT_URL, referanse)
            WiremockListener.wiremock.stubFor(
                WireMock
                    .get(urlEqualTo("/$url"))
                    .willReturn(
                        aResponse()
                            .withHeader(HttpHeaders.ContentType, ContentTypes.APPLICATION_JSON)
                            .withStatus(HttpStatusCode.OK.value),
                    ),
            )

            val response = skeClient.getSkeKravidentifikator(referanse)
            response.status shouldBe HttpStatusCode.OK
        }

        test("alle requests should ha de nødvendige headersene") {
            val kravid = "123"
            val url = String.format(MOTTAKSSTATUS_URL, kravid, KravidentifikatorType.OPPDRAGSGIVERSKRAVIDENTIFIKATOR.value)
            WiremockListener.wiremock.stubFor(
                WireMock
                    .get(urlEqualTo("/$url"))
                    .willReturn(
                        aResponse()
                            .withHeader(HttpHeaders.ContentType, ContentTypes.APPLICATION_JSON)
                            .withStatus(HttpStatusCode.OK.value),
                    ),
            )

            val response =
                skeClient.getMottaksStatus(
                    kravid = kravid,
                    kravidentifikatorType = KravidentifikatorType.OPPDRAGSGIVERSKRAVIDENTIFIKATOR,
                )
            response.status shouldBe HttpStatusCode.OK
        }

        test("Error responses skal håndteres") {
            val kravid = "123"
            val url = String.format(MOTTAKSSTATUS_URL, kravid, KravidentifikatorType.OPPDRAGSGIVERSKRAVIDENTIFIKATOR.value)
            WiremockListener.wiremock.stubFor(
                WireMock
                    .get(urlEqualTo("/$url"))
                    .willReturn(
                        aResponse()
                            .withHeader(HttpHeaders.ContentType, ContentTypes.APPLICATION_JSON)
                            .withStatus(HttpStatusCode.InternalServerError.value),
                    ),
            )

            val response =
                skeClient.getMottaksStatus(
                    kravid = "123",
                    kravidentifikatorType = KravidentifikatorType.OPPDRAGSGIVERSKRAVIDENTIFIKATOR,
                )
            response.status shouldBe HttpStatusCode.InternalServerError
        }
    })
