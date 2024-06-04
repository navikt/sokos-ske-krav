package sokos.ske.krav.service.unit


import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.spyk
import sokos.ske.krav.client.SkeClient
import sokos.ske.krav.database.models.KravTable
import sokos.ske.krav.service.DatabaseService
import sokos.ske.krav.service.StoppKravService
import sokos.ske.krav.util.RequestResult
import sokos.ske.krav.util.mockHttpResponse

class StoppKravServiceTest : FunSpec({

    test("sendAllStoppKrav skal returnere liste av innsendte stopp av krav") {
        val databaseServiceMock = mockk<DatabaseService>() {
            justRun { updateSentKrav(any()) }
        }
        val kravTableMock = mockk<KravTable>() {
            every { kravidentifikatorSKE } returns "foo"
            every { saksnummerNAV } returns "bar"
        }
        val stoppKravMock = spyk(StoppKravService(mockk<SkeClient>(), databaseServiceMock), recordPrivateCalls = true)


        every { stoppKravMock["sendStoppKrav"](any<KravTable>()) } returnsMany listOf(
            RequestResult(mockHttpResponse(404), mockk<KravTable>(), "", "123"),
            RequestResult(mockHttpResponse(200), mockk<KravTable>(), "", "456"),
        )
        val result = stoppKravMock.sendAllStoppKrav(listOf(kravTableMock, kravTableMock))

        result.size shouldBe 2
        result.filter { it.kravidentifikator == "123" }.size shouldBe 1
        result.filter { it.kravidentifikator == "456" }.size shouldBe 1
    }

})
