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
import sokos.ske.krav.service.NYTT_KRAV
import sokos.ske.krav.service.OpprettKravService
import sokos.ske.krav.util.RequestResult
import sokos.ske.krav.util.mockHttpResponse

class OpprettKravServiceTest : FunSpec({

    test("sendAllOpprettKrav skal returnere liste av innsendte nye krav") {
        val databaseServiceMock = mockk<DatabaseService>() {
            justRun { updateSentKrav(any()) }
        }
        val kravTableMock = mockk<KravTable>() {
            every { saksnummerSKE } returns "foo"
            every { saksnummerNAV } returns "bar"
        }
        val opprettKravMock =
            spyk(OpprettKravService(mockk<SkeClient>(), databaseServiceMock), recordPrivateCalls = true)

        every { opprettKravMock["sendAllOpprettKrav"](any<List<KravTable>>()) } returns
                listOf(
                    RequestResult(mockHttpResponse(404), mockk<KravTable>(), "", "123"),
                    RequestResult(mockHttpResponse(404), mockk<KravTable>(), "", "456")
                )

        val result = opprettKravMock.sendAllOpprettKrav(listOf(kravTableMock, kravTableMock))

        result.size shouldBe 2
        result.filter { it.kravidentifikator == "123" }.size shouldBe 1
        result.filter { it.kravidentifikator == "456" }.size shouldBe 1
    }
})