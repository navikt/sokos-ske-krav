package sokos.skd.poc

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import sokos.skd.poc.maskinporten.MaskinportenAccessTokenClient

class SkdClient(
    private val skdEndpoint: String,
    private val client: HttpClient = defaultHttpClient
) {

    suspend fun doPost(path: String, body: String): HttpResponse {
        val token = fetchToken()
        var response: HttpResponse
        runBlocking {
            response = client.post("$skdEndpoint/$path") {
                contentType(ContentType.Application.Json)
                accept(ContentType.Application.Json)
                headers {
                    append(HttpHeaders.Authorization, " bearer $token")
                }
                setBody(body)
            }
            println(response.bodyAsText())
        }
        return response
    }

    suspend fun doGet(path: String) {
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

suspend fun fetchToken() =
    MaskinportenAccessTokenClient(Configuration.MaskinportenClientConfig(), defaultHttpClient).hentAccessToken()

