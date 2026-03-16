package no.nav.sokos.ske.krav.util

import kotlinx.serialization.Serializable

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json

import no.nav.sokos.ske.krav.config.jsonConfig

@Serializable
private data class TestResponse(
    val value: String,
)

class ServiceutilsTest :
    FunSpec({

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

        test("decodeTo skal returnere parsed objekt når parsing er OK") {

            val successResponse = """{"value": "test"}"""
            val client = createMockClient(successResponse)
            val response = client.get("/test").bodyAsText()

            val result = response.decodeTo<TestResponse>()
            result.shouldNotBeNull()
            result.value shouldBe "test"
        }

        test("decodeTo skal returnere null når parsing feiler") {

            val invalidResponse = """{"invalid": json"""
            val client = createMockClient(invalidResponse)
            val response = client.get("/test").bodyAsText()

            val result = response.decodeTo<TestResponse>()

            result.shouldBeNull()
        }
    })
