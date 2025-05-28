package no.nav.sokos.ske.krav.service.unit

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.spyk
import no.nav.sokos.ske.krav.client.SkeClient
import no.nav.sokos.ske.krav.database.models.KravTable
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
            val kravTableMock =
                mockk<KravTable> {
                    every { kravidentifikatorSKE } returns "foo"
                    every { saksnummerNAV } returns "bar"
                }
            val stoppKravMock = spyk(StoppKravService(mockk<SkeClient>(), databaseServiceMock), recordPrivateCalls = true)

            every { stoppKravMock["sendStoppKrav"](any<KravTable>()) } returnsMany
                    listOf(
                        RequestResult(mockHttpResponse(404), mockk<KravTable>(), "", "123", defineStatus(mockHttpResponse(404))),
                        RequestResult(mockHttpResponse(200), mockk<KravTable>(), "", "456", defineStatus(mockHttpResponse(200))),
                    )
            val result = stoppKravMock.sendAllStoppKrav(listOf(kravTableMock, kravTableMock))

            result.size shouldBe 2
            result.filter { it.kravidentifikator == "123" }.size shouldBe 1
            result.filter { it.kravidentifikator == "456" }.size shouldBe 1
        }
    })
