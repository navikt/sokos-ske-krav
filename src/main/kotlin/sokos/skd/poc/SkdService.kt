package sokos.skd.poc

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking

class SkdService(
    private val skdEndpoint: String,
    private val client: HttpClient = defaultHttpClient
) {

    fun doPost(path: String) {
        val token = fetchToken()
        runBlocking {
            val response: HttpResponse = client.post("$skdEndpoint/$path") {
                contentType(ContentType.Application.Json)
                //TODO set post body og headers
                headers {
                    append(HttpHeaders.Authorization, " bearer $token")
                }
                setBody("")
            }
            println(response.bodyAsText())

        }
    }

    fun doGet(path: String) {
        val token = fetchToken()
        runBlocking {
            val response = defaultHttpClient.get("$skdEndpoint/$path") {
                //TODO set post body og headers
                headers {
                    append(HttpHeaders.Authorization, "bearer $token")
                }
            }
            println("Vi fikk: $response")
        }
    }

}

fun fetchToken() = ""

val defaultHttpClient = HttpClient(CIO) {
    expectSuccess = true
    install(Logging){ level = LogLevel.INFO}
}
