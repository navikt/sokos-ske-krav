package no.nav.sokos.ske.krav.util

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json

@Serializable
data class TestResponse(
    val value: String,
)

class ServiceutilsTest :
    FunSpec({
        val jsonConfig =
            Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            }

        val responseHeaders = headersOf("Content-Type" to listOf(ContentType.Application.Json.toString()))

        fun createMockClient(
            response: String,
            statusCode: HttpStatusCode = HttpStatusCode.OK,
        ) = HttpClient(MockEngine) {
            install(ContentNegotiation) {
                json(jsonConfig)
            }
            engine {
                addHandler { respond(response, statusCode, responseHeaders) }
            }
            expectSuccess = false
        }

        test("parseTo skal returnere parsed objekt når parsing er OK") {
            runBlocking {
                val successResponse = """{"value": "test"}"""
                val client = createMockClient(successResponse)
                val response = client.get("/test")

                val result = response.parseTo<TestResponse>()

                result?.value shouldBe "test"
            }
        }

        test("parseTo skal returnere null og logge error når T er FeilResponse") {
            runBlocking {
                val errorResponse = TestData.feilResponse()
                val client = createMockClient(errorResponse, HttpStatusCode.NotFound)
                val response = client.get("/test")

                val result = response.parseTo<TestResponse>()

                result.shouldBeNull()
            }
        }

        test("parseTo skal returnere null og logge generisk feil når all parsing feiler") {
            runBlocking {
                val invalidResponse = """{"invalid": json"""
                val client = createMockClient(invalidResponse)
                val response = client.get("/test")

                val result = response.parseTo<TestResponse>()

                result.shouldBeNull()
            }
        }
    })
