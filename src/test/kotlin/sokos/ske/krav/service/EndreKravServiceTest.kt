package sokos.ske.krav.service

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.call.*
import io.ktor.client.statement.*
import io.mockk.coEvery
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import sokos.ske.krav.client.SkeClient
import sokos.ske.krav.database.models.KravTable
import sokos.ske.krav.database.models.Status
import sokos.ske.krav.domain.ske.requests.EndreRenteBeloepRequest
import sokos.ske.krav.domain.ske.requests.Kravidentifikatortype
import sokos.ske.krav.domain.ske.requests.NyHovedStolRequest
import sokos.ske.krav.domain.ske.responses.FeilResponse
import java.time.LocalDate
import java.time.LocalDateTime

class EndreKravServiceTest : FunSpec({


    test("hvis reposnsen er 404 og 422 skal det være 404") {

        val httpResponseMock404 = mockk<HttpResponse>() {
            every { status.value } returns 404
            coEvery { body<FeilResponse>() } returns FeilResponse("type", "title", 404, "detail", "instance")
        }
        val httpResponseMock422 = mockk<HttpResponse>() {
            every { status.value } returns 422
            coEvery { body<FeilResponse>() } returns FeilResponse("type", "title", 422, "detail", "instance")
        }

        val skeClientMock = mockk<SkeClient>() {
            coEvery { endreRenter(any< EndreRenteBeloepRequest>(), any<String>(), any<Kravidentifikatortype>(), any<String>()) }  returns httpResponseMock422
            coEvery { endreHovedstol(any<NyHovedStolRequest>(), any<String>(), any<Kravidentifikatortype>(), any<String>()) }  returns httpResponseMock404
        }
        val databaseSericeMock = mockk<DatabaseService>()
        val endreKravService = EndreKravService(skeClientMock, databaseSericeMock)
        justRun { databaseSericeMock.updateSentKravToDatabase(any()) }


        val kravListe = listOf(
            KravTable( 111, "skeref123", "navref123", 1001.0, LocalDate.now(), "12345678901", "20230112","20233112", "KS KS", "navoldref123", "20240105", "0408", "0408", "T", "", 0.0, 0.0, LocalDate.now(), "","KRAV_IKKE_SENDT", "ENDRE_RENTER", "cor123", LocalDateTime.now(),LocalDateTime.now(),LocalDateTime.now()),
            KravTable( 112, "skeref123", "navref123", 1001.0, LocalDate.now(), "12345678901", "20230112","20233112", "KS KS", "navoldref123", "20240105", "0408", "0408", "T", "", 0.0, 0.0, LocalDate.now(), "","KRAV_IKKE_SENDT", "ENDRE_HOVEDSTOL", "cor124", LocalDateTime.now(),LocalDateTime.now(),LocalDateTime.now()),
        )
        val result = endreKravService.sendAllEndreKrav(kravListe).flatMap { it.entries.toList() }.map { it.value }

        result.filter { it.status == Status.ANNEN_IKKE_FUNNET_404 }.size shouldBe 2
    }

    test("hvis responsen er 409 og 422 skal det være 422") {

        val skeClientMock = mockk<SkeClient>()
        val databaseSericeMock = mockk<DatabaseService>()
        val endreKravService = EndreKravService(skeClientMock, databaseSericeMock)
        justRun { databaseSericeMock.updateSentKravToDatabase(any()) }
        val httpResponseMock409 = mockk<HttpResponse>() {
            every { status.value } returns 409
            coEvery { body<FeilResponse>() } returns FeilResponse("type", "title", 409, "detail", "instance")
        }
        val httpResponseMock422 = mockk<HttpResponse>() {
            every { status.value } returns 422
            coEvery { body<FeilResponse>() } returns FeilResponse("type", "title", 422, "detail", "instance")
        }

        val kravListe = listOf(
            KravTable( 111, "skeref123", "navref123", 1001.0, LocalDate.now(), "12345678901", "20230112","20233112", "KS KS", "navoldref123", "20240105", "0408", "0408", "T", "", 0.0, 0.0, LocalDate.now(), "","KRAV_IKKE_SENDT", "ENDRE_RENTER", "cor123", LocalDateTime.now(),LocalDateTime.now(),LocalDateTime.now()),
            KravTable( 111, "skeref123", "navref123", 1001.0, LocalDate.now(), "12345678901", "20230112","20233112", "KS KS", "navoldref123", "20240105", "0408", "0408", "T", "", 0.0, 0.0, LocalDate.now(), "","KRAV_IKKE_SENDT", "ENDRE_HOVEDSTOL", "cor124", LocalDateTime.now(),LocalDateTime.now(),LocalDateTime.now()),
        )
        coEvery { skeClientMock.endreRenter(any(), any(), any(), any()) } returns httpResponseMock422
        coEvery { skeClientMock.endreHovedstol(any(), any(), any(), any()) } returns httpResponseMock409

        val result = endreKravService.sendAllEndreKrav(kravListe).flatMap { it.entries.toList() }.map { it.value }

        result.filter { it.status == Status.VALIDERINGSFEIL_422 }.size shouldBe 2
    }

    test("hvis responsen er 409 og 404 skal det være 404") {

        val skeClientMock = mockk<SkeClient>()
        val databaseSericeMock = mockk<DatabaseService>()
        val endreKravService = EndreKravService(skeClientMock, databaseSericeMock)
        justRun { databaseSericeMock.updateSentKravToDatabase(any()) }
        val httpResponseMock409 = mockk<HttpResponse>() {
            every { status.value } returns 409
            coEvery { body<FeilResponse>() } returns FeilResponse("type", "title", 409, "detail", "instance")
        }
        val httpResponseMock404 = mockk<HttpResponse>() {
            every { status.value } returns 404
            coEvery { body<FeilResponse>() } returns FeilResponse("type", "title", 404, "detail", "instance")
        }

        val kravListe = listOf(
            KravTable( 111, "skeref123", "navref123", 1001.0, LocalDate.now(), "12345678901", "20230112","20233112", "KS KS", "navoldref123", "20240105", "0408", "0408", "T", "", 0.0, 0.0, LocalDate.now(), "","KRAV_IKKE_SENDT", "ENDRE_RENTER", "cor123", LocalDateTime.now(),LocalDateTime.now(),LocalDateTime.now()),
            KravTable( 111, "skeref123", "navref123", 1001.0, LocalDate.now(), "12345678901", "20230112","20233112", "KS KS", "navoldref123", "20240105", "0408", "0408", "T", "", 0.0, 0.0, LocalDate.now(), "","KRAV_IKKE_SENDT", "ENDRE_HOVEDSTOL", "cor124", LocalDateTime.now(),LocalDateTime.now(),LocalDateTime.now()),
        )
        coEvery { skeClientMock.endreRenter(any(), any(), any(), any()) } returns httpResponseMock404
        coEvery { skeClientMock.endreHovedstol(any(), any(), any(), any()) } returns httpResponseMock409

        val result = endreKravService.sendAllEndreKrav(kravListe).flatMap { it.entries.toList() }.map { it.value }

        result.filter { it.status == Status.ANNEN_IKKE_FUNNET_404 }.size shouldBe 2
    }

    test("sender 10 endringer 5 rente og 5 hovedstol skal gi 1 200, 2 404, 1 409 og 1 422") {

        val skeClientMock = mockk<SkeClient>()
        val databaseSericeMock = mockk<DatabaseService>()
        val endreKravService = EndreKravService(skeClientMock, databaseSericeMock)
        justRun { databaseSericeMock.updateSentKravToDatabase(any()) }

        val httpResponseMock200 = mockk<HttpResponse>() {
            every { status.value } returns 200
            coEvery { body<FeilResponse>() } returns FeilResponse("type", "title", 200, "detail", "instance")
        }
        val httpResponseMock409 = mockk<HttpResponse>() {
            every { status.value } returns 409
            coEvery { body<FeilResponse>() } returns FeilResponse("type", "title", 409, "detail", "instance")
        }
        val httpResponseMock404 = mockk<HttpResponse>() {
            every { status.value } returns 404
            coEvery { body<FeilResponse>() } returns FeilResponse("type", "title", 404, "detail", "instance")
        }
        val httpResponseMock422 = mockk<HttpResponse>() {
            every { status.value } returns 422
            coEvery { body<FeilResponse>() } returns FeilResponse("type", "title", 422, "detail", "instance")// andThen FeilResponse("type", "title", 200, "detail", "instance")
        }

        val kravListe = listOf(
            KravTable( 111, "skeref123", "navref123", 1001.0, LocalDate.now(), "12345678901", "20230112","20233112", "KS KS", "navoldref123", "20240105", "0408", "0408", "T", "", 0.0, 0.0, LocalDate.now(), "","KRAV_IKKE_SENDT", "ENDRE_RENTER", "cor123", LocalDateTime.now(),LocalDateTime.now(),LocalDateTime.now()),
            KravTable( 111, "skeref123", "navref123", 1001.0, LocalDate.now(), "12345678901", "20230112","20233112", "KS KS", "navoldref123", "20240105", "0408", "0408", "T", "", 0.0, 0.0, LocalDate.now(), "","KRAV_IKKE_SENDT", "ENDRE_HOVEDSTOL", "cor124", LocalDateTime.now(),LocalDateTime.now(),LocalDateTime.now()),
            KravTable( 111, "skeref223", "navref223", 1002.0, LocalDate.now(), "12345678901", "20230112","20233112", "KS KS", "navoldref223", "20240105", "0408", "0408", "T", "", 0.0, 0.0, LocalDate.now(), "","KRAV_IKKE_SENDT", "ENDRE_RENTER", "cor223", LocalDateTime.now(),LocalDateTime.now(),LocalDateTime.now()),
            KravTable( 111, "skeref223", "navref223", 1002.0, LocalDate.now(), "12345678901", "20230112","20233112", "KS KS", "navoldref223", "20240105", "0408", "0408", "T", "", 0.0, 0.0, LocalDate.now(), "","KRAV_IKKE_SENDT", "ENDRE_HOVEDSTOL", "cor224", LocalDateTime.now(),LocalDateTime.now(),LocalDateTime.now()),
            KravTable( 111, "skeref323", "navref323", 1003.0, LocalDate.now(), "12345678901", "20230112","20233112", "KS KS", "navoldref323", "20240105", "0408", "0408", "T", "", 0.0, 0.0, LocalDate.now(), "","KRAV_IKKE_SENDT", "ENDRE_RENTER", "cor323", LocalDateTime.now(),LocalDateTime.now(),LocalDateTime.now()),
            KravTable( 111, "skeref323", "navref323", 1003.0, LocalDate.now(), "12345678901", "20230112","20233112", "KS KS", "navoldref323", "20240105", "0408", "0408", "T", "", 0.0, 0.0, LocalDate.now(), "","KRAV_IKKE_SENDT", "ENDRE_HOVEDSTOL", "cor324", LocalDateTime.now(),LocalDateTime.now(),LocalDateTime.now()),
            KravTable( 111, "skeref423", "navref423", 1004.0, LocalDate.now(), "12345678901", "20230112","20233112", "KS KS", "navoldref423", "20240105", "0408", "0408", "T", "", 0.0, 0.0, LocalDate.now(), "","KRAV_IKKE_SENDT", "ENDRE_RENTER", "cor423", LocalDateTime.now(),LocalDateTime.now(),LocalDateTime.now()),
            KravTable( 111, "skeref423", "navref423", 1004.0, LocalDate.now(), "12345678901", "20230112","20233112", "KS KS", "navoldref423", "20240105", "0408", "0408", "T", "", 0.0, 0.0, LocalDate.now(), "","KRAV_IKKE_SENDT", "ENDRE_HOVEDSTOL", "cor424", LocalDateTime.now(),LocalDateTime.now(),LocalDateTime.now()),
            KravTable( 111, "skeref523", "navref523", 1005.0, LocalDate.now(), "12345678901", "20230112","20233112", "KS KS", "navoldref523", "20240105", "0408", "0408", "T", "", 0.0, 0.0, LocalDate.now(), "","KRAV_IKKE_SENDT", "ENDRE_RENTER", "cor523", LocalDateTime.now(),LocalDateTime.now(),LocalDateTime.now()),
            KravTable( 111, "skeref523", "navref523", 1005.0, LocalDate.now(), "12345678901", "20230112","20233112", "KS KS", "navoldref523", "20240105", "0408", "0408", "T", "", 0.0, 0.0, LocalDate.now(), "","KRAV_IKKE_SENDT", "ENDRE_HOVEDSTOL", "cor524", LocalDateTime.now(),LocalDateTime.now(),LocalDateTime.now()),
        )
        coEvery { skeClientMock.endreRenter(any(), "skeref123", any(), any()) } returns httpResponseMock200
        coEvery { skeClientMock.endreHovedstol(any(), "skeref123", any(), any()) } returns httpResponseMock409
        coEvery { skeClientMock.endreRenter(any(), "skeref223", any(), any()) } returns httpResponseMock200
        coEvery { skeClientMock.endreHovedstol(any(), "skeref223", any(), any()) } returns httpResponseMock200
        coEvery { skeClientMock.endreRenter(any(), "skeref323", any(), any()) } returns httpResponseMock422
        coEvery { skeClientMock.endreHovedstol(any(), "skeref323", any(), any()) } returns httpResponseMock409
        coEvery { skeClientMock.endreRenter(any(), "skeref423", any(), any()) } returns httpResponseMock404
        coEvery { skeClientMock.endreHovedstol(any(), "skeref423", any(), any()) } returns httpResponseMock409
        coEvery { skeClientMock.endreRenter(any(), "skeref523", any(), any()) } returns httpResponseMock404
        coEvery { skeClientMock.endreHovedstol(any(), "skeref523", any(), any()) } returns httpResponseMock422

        val result = endreKravService.sendAllEndreKrav(kravListe).flatMap { it.entries.toList() }.map { it.value }

//        val konflikt = result.filter { it.status ==Status.ANNEN_KONFLIKT }.size
//        val ok = result.filter { it.status ==Status.KRAV_SENDT }.size
//        val validering = result.filter { it.status ==Status.VALIDERINGSFEIL }.size
//        val ikkeFunnet = result.filter { it.status ==Status.FANT_IKKE_SAKSREF }.size
//
//        println("Antall responser: ${result.size}")
//        println("Konflikter: $konflikt")
//        println("ok: $ok")
//        println("Validerin: $validering")
//        println("Ikke funnet: $ikkeFunnet")

        result.size shouldBe 10
        result.filter { it.status == Status.ANNEN_KONFLIKT_409 }.size shouldBe 2
        result.filter { it.status == Status.KRAV_SENDT }.size shouldBe 2
        result.filter { it.status == Status.VALIDERINGSFEIL_422 }.size shouldBe 2
        result.filter { it.status == Status.ANNEN_IKKE_FUNNET_404 }.size shouldBe 4

    }


})

