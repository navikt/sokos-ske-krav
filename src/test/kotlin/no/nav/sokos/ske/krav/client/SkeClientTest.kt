package no.nav.sokos.ske.krav.client

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondOk
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import no.nav.sokos.ske.krav.domain.StonadsType
import no.nav.sokos.ske.krav.domain.ske.requests.AvskrivingRequest
import no.nav.sokos.ske.krav.domain.ske.requests.EndreRenteBeloepRequest
import no.nav.sokos.ske.krav.domain.ske.requests.HovedstolBeloep
import no.nav.sokos.ske.krav.domain.ske.requests.KravidentifikatorType
import no.nav.sokos.ske.krav.domain.ske.requests.OpprettInnkrevingsoppdragRequest
import no.nav.sokos.ske.krav.domain.ske.requests.RenteBeloep
import no.nav.sokos.ske.krav.domain.ske.requests.Skyldner
import no.nav.sokos.ske.krav.security.MaskinportenAccessTokenProvider
import java.util.UUID

class SkeClientTest :
    FunSpec({
        val mockToken = "mock-token"
        val mockTokenClient =
            mockk<MaskinportenAccessTokenProvider> {
                coEvery { getAccessToken() } returns mockToken
            }

        val mockEngine =
            MockEngine { request ->
                request.headers["Klientid"] shouldBe "NAV/0.1"
                request.headers["Korrelasjonsid"] shouldNotBe null
                request.headers[HttpHeaders.Authorization] shouldBe "Bearer $mockToken"

                if (request.method.value in listOf("POST", "PUT")) {
                    request.body.contentType shouldBe ContentType.Application.Json
                    request.headers[HttpHeaders.Accept] shouldBe ContentType.Application.Json.toString()
                    request.body.contentLength shouldNotBe 0L
                }

                when {
                    request.url.encodedPath.endsWith("/innkrevingsoppdrag") -> {
                        respond(
                            content = "{}",
                            status = HttpStatusCode.Created,
                            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                        )
                    }
                    request.url.encodedPath.contains("/renter") -> {
                        request.headers["kravidentifikator"] shouldNotBe null
                        respond(
                            content = "{}",
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                        )
                    }
                    request.url.encodedPath.contains("/hovedstol") -> {
                        request.headers["kravidentifikator"] shouldNotBe null
                        respond(
                            content = "{}",
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                        )
                    }
                    request.url.encodedPath.contains("/avskriving") -> {
                        respond(
                            content = "{}",
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                        )
                    }
                    request.url.encodedPath.contains("/mottaksstatus") -> {
                        respond(
                            content = """{"status": "MOTTATT"}""",
                            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                        )
                    }
                    request.url.encodedPath.contains("/valideringsfeil") -> {
                        respond(
                            content = """{"feil": []}""",
                            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                        )
                    }
                    request.url.encodedPath.contains("/avstemming") -> {
                        respond(
                            content = """{"kravidentifikator": "123"}""",
                            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                        )
                    }
                    else ->
                        respond(
                            content = "Not Found",
                            status = HttpStatusCode.NotFound,
                        )
                }
            }

        val mockClient =
            HttpClient(mockEngine) {
                install(ContentNegotiation) {
                    json(
                        Json {
                            prettyPrint = true
                            isLenient = true
                            ignoreUnknownKeys = true
                        },
                    )
                }
                expectSuccess = false
            }

        val skeClient =
            SkeClient(
                tokenProvider = mockTokenClient,
                skeEndpoint = "http://test-ske-endpoint/",
                client = mockClient,
            )

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

            val response = skeClient.stoppKrav(request, corrId)
            response.status shouldBe HttpStatusCode.OK
        }

        test("getValideringsfeil skal sende GET request med korrekt headers") {
            val kravid = "123"

            val response =
                skeClient.getValideringsfeil(
                    kravid = kravid,
                    kravidentifikatorType = KravidentifikatorType.OPPDRAGSGIVERSKRAVIDENTIFIKATOR,
                )
            response.status shouldBe HttpStatusCode.OK
        }

        test("getSkeKravidentifikator skal sende GET requestmed korrekt headers") {
            val referanse = "123"

            val response = skeClient.getSkeKravidentifikator(referanse)
            response.status shouldBe HttpStatusCode.OK
        }

        test("alle requests should ha de nødvendige headersene") {
            val mockEngine =
                MockEngine { request ->
                    request.headers["Klientid"] shouldBe "NAV/0.1"
                    request.headers["Korrelasjonsid"] shouldBe request.headers["Korrelasjonsid"]
                    request.headers[HttpHeaders.Authorization] shouldBe "Bearer $mockToken"
                    respondOk()
                }

            val client =
                SkeClient(
                    tokenProvider = mockTokenClient,
                    skeEndpoint = "http://test-ske-endpoint/",
                    client = HttpClient(mockEngine),
                )

            client.getMottaksStatus(
                kravid = "123",
                kravidentifikatorType = KravidentifikatorType.OPPDRAGSGIVERSKRAVIDENTIFIKATOR,
            )
        }

        test("Error responses skal håndteres") {
            val errorMockEngine =
                MockEngine {
                    respond(
                        content = "Error occurred",
                        status = HttpStatusCode.InternalServerError,
                    )
                }

            val client =
                SkeClient(
                    tokenProvider = mockTokenClient,
                    skeEndpoint = "http://test-ske-endpoint/",
                    client = HttpClient(errorMockEngine),
                )

            val response =
                client.getMottaksStatus(
                    kravid = "123",
                    kravidentifikatorType = KravidentifikatorType.OPPDRAGSGIVERSKRAVIDENTIFIKATOR,
                )
            response.status shouldBe HttpStatusCode.InternalServerError
        }
    })
