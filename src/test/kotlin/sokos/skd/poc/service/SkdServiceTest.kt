package sokos.skd.poc.service


import io.kotest.common.runBlocking
import io.kotest.core.annotation.Ignored
import io.kotest.core.spec.style.FunSpec
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.utils.io.*
import io.mockk.mockk
import sokos.skd.poc.SkdClient

import sokos.skd.poc.maskinporten.MaskinportenAccessTokenClient

@Ignored
internal class SkdServiceTest: FunSpec ({

    test("foo"){
        runBlocking {
            val tokenProvider = mockk<MaskinportenAccessTokenClient>(relaxed = true)

            val mockEngineOK = MockEngine {
                respond(
                    content = ByteReadChannel(""),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }

            val client = SkdClient(tokenProvider, "", engine = mockEngineOK)
           // SkdService(client).sjekkOmNyFilOgSendTilSkatt(1)
          // val responses = SkdService(skdClient = client).sendNyeFtpFilerTilSkatt()

        }
    }
})