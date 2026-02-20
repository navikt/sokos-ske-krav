package no.nav.sokos.ske.krav.service.unit

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.spyk

import no.nav.sokos.ske.krav.client.SkeClient
import no.nav.sokos.ske.krav.domain.Krav
import no.nav.sokos.ske.krav.service.DatabaseService
import no.nav.sokos.ske.krav.service.StoppKravService
import no.nav.sokos.ske.krav.util.RequestResult
import no.nav.sokos.ske.krav.util.defineStatus
import no.nav.sokos.ske.krav.util.mockHttpResponse

class StoppKravServiceTest :
    FunSpec({

        test("sendAllStoppKrav skal returnere liste av innsendte stopp av krav") {
            val databaseServiceMock =
                mockk<DatabaseService> {
                    justRun { updateSentKrav(any<List<RequestResult>>()) }
                }
            val kravMock =
                mockk<Krav> {
                    every { kravidentifikatorSKE } returns "foo"
                    every { saksnummerNAV } returns "bar"
                }
            val stoppKravMock = spyk(StoppKravService(mockk<SkeClient>(), databaseServiceMock), recordPrivateCalls = true)
            val firstStatusResponse = mockHttpResponse(404)
            val secondStatusResponse = mockHttpResponse(200)
            every { stoppKravMock["sendStoppKrav"](any<Krav>()) } returnsMany
                listOf(
                    RequestResult(
                        firstStatusResponse,
                        mockk<Krav>(),
                        "",
                        "123",
                        defineStatus("body", firstStatusResponse.status),
                    ),
                    RequestResult(
                        secondStatusResponse,
                        mockk<Krav>(),
                        "",
                        "456",
                        defineStatus("body", secondStatusResponse.status),
                    ),
                )
            val result = stoppKravMock.sendAllStoppKrav(listOf(kravMock, kravMock))

            result.size shouldBe 2
            result.filter { it.kravidentifikator == "123" }.size shouldBe 1
            result.filter { it.kravidentifikator == "456" }.size shouldBe 1
        }
    })
