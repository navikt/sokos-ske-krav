package sokos.ske.krav.service

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.justRun
import io.mockk.mockk
import sokos.ske.krav.client.SkeClient
import sokos.ske.krav.database.models.KravTable
import sokos.ske.krav.database.models.Status
import sokos.ske.krav.domain.ske.requests.EndreRenteBeloepRequest
import sokos.ske.krav.domain.ske.requests.Kravidentifikatortype
import sokos.ske.krav.domain.ske.requests.NyHovedStolRequest
import sokos.ske.krav.util.RequestResult
import sokos.ske.krav.util.mockHttpResponse
import java.time.LocalDate
import java.time.LocalDateTime

internal class EndreKravServiceTest : FunSpec({

    val databaseServiceMock = mockk<DatabaseService>() {
        justRun { updateSentKravToDatabase(any<List<Map<String, RequestResult>>>()) }
    }

    val kravListe = listOf(
        KravTable(111, "skeref123", "navref123", 1001.0, LocalDate.now(), "12345678901", "20230112", "20233112", "KS KS", "navoldref123", "20240105", "0408", "0408", "T", "", 0.0, 0.0, LocalDate.now(), "", "KRAV_IKKE_SENDT", "ENDRE_RENTER", "cor123", LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now()),
        KravTable(112, "skeref123", "navref123", 1001.0, LocalDate.now(), "12345678901", "20230112", "20233112", "KS KS", "navoldref123", "20240105", "0408", "0408", "T", "", 0.0, 0.0, LocalDate.now(), "", "KRAV_IKKE_SENDT", "ENDRE_HOVEDSTOL", "cor124", LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now()),
    )

    test("hvis responsen er 404 og 422 skal status settes til en 404 status") {

        val skeClientMock = mockk<SkeClient>() {
            coEvery { endreRenter(any<EndreRenteBeloepRequest>(), any<String>(), any<Kravidentifikatortype>(), any<String>()) } returns mockHttpResponse(422)
            coEvery { endreHovedstol(any<NyHovedStolRequest>(), any<String>(), any<Kravidentifikatortype>(), any<String>()) } returns mockHttpResponse(404)
        }

        val result = EndreKravService(skeClientMock, databaseServiceMock).sendAllEndreKrav(kravListe).flatMap { it.entries.toList() }.map { it.value }

        result.filter { it.status == Status.ANNEN_IKKE_FUNNET_404 || it.status == Status.FANT_IKKE_SAKSREF_404 }.size shouldBe 2

    }

    test("hvis responsen er 409 og 422 skal begge få status Status.VALIDERINGSFEIL_422") {

        val skeClientMock = mockk<SkeClient>() {
            coEvery { endreRenter(any<EndreRenteBeloepRequest>(), any<String>(), any<Kravidentifikatortype>(), any<String>()) } returns mockHttpResponse(422)
            coEvery { endreHovedstol(any<NyHovedStolRequest>(), any<String>(), any<Kravidentifikatortype>(), any<String>()) } returns mockHttpResponse(409)
        }

        val result = EndreKravService(skeClientMock, databaseServiceMock).sendAllEndreKrav(kravListe).flatMap { it.entries.toList() }.map { it.value }

        result.filter { it.status == Status.VALIDERINGSFEIL_422 }.size shouldBe 2
    }

    test("hvis responsen er 409 og 404 skal status settes til en 404 status") {

        val skeClientMock = mockk<SkeClient>() {
            coEvery { endreRenter(any<EndreRenteBeloepRequest>(), any<String>(), any<Kravidentifikatortype>(), any<String>()) } returns mockHttpResponse(404)
            coEvery { endreHovedstol(any<NyHovedStolRequest>(), any<String>(), any<Kravidentifikatortype>(), any<String>()) } returns mockHttpResponse(409)
        }

        val result = EndreKravService(skeClientMock, databaseServiceMock).sendAllEndreKrav(kravListe).flatMap { it.entries.toList() }.map { it.value }

        result.filter { it.status == Status.ANNEN_IKKE_FUNNET_404 || it.status == Status.FANT_IKKE_SAKSREF_404 }.size shouldBe 2
    }

    test("hvis responsen er 409 og 200 skal status settes til en 409 status") {

        val skeClientMock = mockk<SkeClient>() {
            coEvery { endreRenter(any<EndreRenteBeloepRequest>(), any<String>(), any<Kravidentifikatortype>(), any<String>()) } returns mockHttpResponse(200)
            coEvery { endreHovedstol(any<NyHovedStolRequest>(), any<String>(), any<Kravidentifikatortype>(), any<String>()) } returns mockHttpResponse(409)
        }

        val result = EndreKravService(skeClientMock, databaseServiceMock).sendAllEndreKrav(kravListe).flatMap { it.entries.toList() }.map { it.value }
        println(result)

        result.filter { it.status == Status.ANNEN_KONFLIKT_409 || it.status == Status.KRAV_ER_AVSKREVET_409 }.size shouldBe 2
    }

})
