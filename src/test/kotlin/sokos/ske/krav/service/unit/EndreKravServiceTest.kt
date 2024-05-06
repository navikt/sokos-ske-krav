package sokos.ske.krav.service.unit

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.spyk
import sokos.ske.krav.client.SkeClient
import sokos.ske.krav.database.models.KravTable
import sokos.ske.krav.domain.Status
import sokos.ske.krav.domain.ske.requests.KravidentifikatorType
import sokos.ske.krav.service.DatabaseService
import sokos.ske.krav.service.EndreKravService
import sokos.ske.krav.util.RequestResult
import sokos.ske.krav.util.mockHttpResponse

internal class EndreKravServiceTest : FunSpec({

    val databaseServiceMock = mockk<DatabaseService>() {
        justRun { updateSentKrav(any()) }
    }

    val endreKravMock = spyk(EndreKravService(mockk<SkeClient>(), databaseServiceMock), recordPrivateCalls = true)
    val kravTableMock = mockk<KravTable>() {
        every { saksnummerSKE } returns "foo"
        every { saksnummerNAV } returns "bar"
    }


    test("Hvis første status er 404 og andre status er 422 skal status for begge settes til 404") {

        every {
            endreKravMock["sendEndreKrav"](
                any<String>(),
                any<KravidentifikatorType>(),
                any<KravTable>()
            )
        } returnsMany listOf(
            RequestResult(mockHttpResponse(404), mockk<KravTable>(), "", ""),
            RequestResult(mockHttpResponse(422), mockk<KravTable>(), "", ""),
        )

        val result = endreKravMock.sendAllEndreKrav(listOf(kravTableMock, kravTableMock))

        result.filter { it.status == Status.ANNEN_IKKE_FUNNET_404 || it.status == Status.FANT_IKKE_SAKSREF_404 }.size shouldBe 2
    }


    test("Hvis første status er 409 og andre status er 422 skal status for begge settes til 409") {

        every {
            endreKravMock["sendEndreKrav"](
                any<String>(),
                any<KravidentifikatorType>(),
                any<KravTable>()
            )
        } returnsMany listOf(
            RequestResult(mockHttpResponse(409), mockk<KravTable>(), "", ""),
            RequestResult(mockHttpResponse(422), mockk<KravTable>(), "", ""),
        )

        val result = endreKravMock.sendAllEndreKrav(listOf(kravTableMock, kravTableMock))

        result.filter { it.status == Status.VALIDERINGSFEIL_422 }.size shouldBe 2
    }

    test("Hvis første status er 409 og andre status er 404 skal status for begge settes til 404") {

        every {
            endreKravMock["sendEndreKrav"](
                any<String>(),
                any<KravidentifikatorType>(),
                any<KravTable>()
            )
        } returnsMany listOf(
            RequestResult(mockHttpResponse(409), mockk<KravTable>(), "", ""),
            RequestResult(mockHttpResponse(404), mockk<KravTable>(), "", ""),
        )

        val result = endreKravMock.sendAllEndreKrav(listOf(kravTableMock, kravTableMock))

        result.filter { it.status == Status.ANNEN_IKKE_FUNNET_404 || it.status == Status.FANT_IKKE_SAKSREF_404 }.size shouldBe 2
    }

    test("Hvis første status er 409 og andre status er 200 skal status for begge settes til 409") {
        every {
            endreKravMock["sendEndreKrav"](
                any<String>(),
                any<KravidentifikatorType>(),
                any<KravTable>()
            )
        } returnsMany listOf(
            RequestResult(mockHttpResponse(409), mockk<KravTable>(), "", ""),
            RequestResult(mockHttpResponse(200), mockk<KravTable>(), "", ""),
        )

        val result = endreKravMock.sendAllEndreKrav(listOf(kravTableMock, kravTableMock))

        result.filter { it.status == Status.ANNEN_KONFLIKT_409 || it.status == Status.KRAV_ER_AVSKREVET_409 }.size shouldBe 2
    }


    test("Hvis første status er 200 og andre status er 422 skal status for begge settes til 422") {
        every {
            endreKravMock["sendEndreKrav"](
                any<String>(),
                any<KravidentifikatorType>(),
                any<KravTable>()
            )
        } returnsMany listOf(
            RequestResult(mockHttpResponse(200), mockk<KravTable>(), "", ""),
            RequestResult(mockHttpResponse(422), mockk<KravTable>(), "", ""),
        )

        val result = endreKravMock.sendAllEndreKrav(listOf(kravTableMock, kravTableMock))

        result.filter { it.status == Status.VALIDERINGSFEIL_422 }.size shouldBe 2
    }


    test("hvis responsen ikke er 404, 409, eller 422 skal status settes til ukjent") {
        every {
            endreKravMock["sendEndreKrav"](
                any<String>(),
                any<KravidentifikatorType>(),
                any<KravTable>()
            )
        } returnsMany listOf(
            RequestResult(mockHttpResponse(102), mockk<KravTable>(), "", "", Status.KRAV_ER_AVSKREVET_409),
            RequestResult(mockHttpResponse(102), mockk<KravTable>(), "", "", Status.ANNEN_SERVER_FEIL_500),
        )

        val result = endreKravMock.sendAllEndreKrav(listOf(kravTableMock, kravTableMock))

        println(result.map { it.status })
         result.filter { it.status == Status.UKJENT_STATUS }.size shouldBe 2
    }

})
