package sokos.ske.krav.service.unit

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.spyk
import sokos.ske.krav.client.SkeClient
import sokos.ske.krav.database.models.KravTable
import sokos.ske.krav.database.models.Status
import sokos.ske.krav.domain.ske.requests.KravidentifikatorType
import sokos.ske.krav.service.DatabaseService
import sokos.ske.krav.service.ENDRING_HOVEDSTOL
import sokos.ske.krav.service.ENDRING_RENTE
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

    test("hvis responsen er 404 og 422 skal status settes til en 404 status") {

        every { endreKravMock["sendEndreKrav"](any<String>(), any<KravidentifikatorType>(), any<KravTable>()) } returnsMany listOf(
            mapOf(
                ENDRING_HOVEDSTOL to RequestResult(mockHttpResponse(404), mockk<KravTable>(), "", "", "")
            ), mapOf(
                ENDRING_RENTE to RequestResult(mockHttpResponse(422), mockk<KravTable>(), "", "", ""),
            )
        )

        val result = endreKravMock.sendAllEndreKrav(listOf(kravTableMock, kravTableMock)).flatMap { it.entries.toList() }.map { it.value }

        result.filter { it.status == Status.ANNEN_IKKE_FUNNET_404 || it.status == Status.FANT_IKKE_SAKSREF_404 }.size shouldBe 2
    }

    test("conform") {


    }

    test("hvis responsen er 409 og 422 skal begge få status Status.VALIDERINGSFEIL_422") {

        every { endreKravMock["sendEndreKrav"](any<String>(), any<KravidentifikatorType>(), any<KravTable>()) } returnsMany listOf(
            mapOf(
                ENDRING_HOVEDSTOL to RequestResult(mockHttpResponse(409), mockk<KravTable>(), "", "", "")
            ), mapOf(
                ENDRING_RENTE to RequestResult(mockHttpResponse(422), mockk<KravTable>(), "", "", ""),
            )
        )

        val result = endreKravMock.sendAllEndreKrav(listOf(kravTableMock, kravTableMock)).flatMap { it.entries.toList() }.map { it.value }

        result.filter { it.status == Status.VALIDERINGSFEIL_422 }.size shouldBe 2
    }

    test("hvis responsen er 409 og 404 skal status settes til en 404 status") {

        every { endreKravMock["sendEndreKrav"](any<String>(), any<KravidentifikatorType>(), any<KravTable>()) } returnsMany listOf(
            mapOf(
                ENDRING_HOVEDSTOL to RequestResult(mockHttpResponse(409), mockk<KravTable>(), "", "", "")
            ), mapOf(
                ENDRING_RENTE to RequestResult(mockHttpResponse(404), mockk<KravTable>(), "", "", ""),
            )
        )

        val result = endreKravMock.sendAllEndreKrav(listOf(kravTableMock, kravTableMock)).flatMap { it.entries.toList() }.map { it.value }

        result.filter { it.status == Status.ANNEN_IKKE_FUNNET_404 || it.status == Status.FANT_IKKE_SAKSREF_404 }.size shouldBe 2
    }

    test("hvis responsen er 409 og 200 skal status settes til en 409 status") {
        every { endreKravMock["sendEndreKrav"](any<String>(), any<KravidentifikatorType>(), any<KravTable>()) } returnsMany listOf(
            mapOf(
                ENDRING_HOVEDSTOL to RequestResult(mockHttpResponse(409), mockk<KravTable>(), "", "", "")
            ), mapOf(
                ENDRING_RENTE to RequestResult(mockHttpResponse(200), mockk<KravTable>(), "", "", ""),
            )
        )

        val result = endreKravMock.sendAllEndreKrav(listOf(kravTableMock, kravTableMock)).flatMap { it.entries.toList() }.map { it.value }

        result.filter { it.status == Status.ANNEN_KONFLIKT_409 || it.status == Status.KRAV_ER_AVSKREVET_409 }.size shouldBe 2
    }

})
