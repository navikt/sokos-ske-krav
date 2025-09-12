package no.nav.sokos.ske.krav.listener

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.configureFor
import io.kotest.core.listeners.TestListener
import io.kotest.core.test.TestCase
import io.mockk.coEvery
import io.mockk.mockk

import no.nav.sokos.ske.krav.security.MaskinportenAccessTokenProvider

private const val WIREMOCK_SERVER_PORT = 9001

object WiremockListener : TestListener {
    val wiremock = WireMockServer(WIREMOCK_SERVER_PORT)
    val mockToken = "mock-token"
    val mockTokenClient =
        mockk<MaskinportenAccessTokenProvider> {
            coEvery { getAccessToken() } returns mockToken
        }

    override suspend fun beforeAny(testCase: TestCase) {
        configureFor(WIREMOCK_SERVER_PORT)
        if (!wiremock.isRunning) {
            wiremock.start()
        }
    }
}
