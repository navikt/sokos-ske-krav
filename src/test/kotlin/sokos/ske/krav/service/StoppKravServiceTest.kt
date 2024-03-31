package sokos.ske.krav.service


import io.kotest.core.spec.style.ShouldSpec
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
import sokos.ske.krav.domain.ske.responses.FeilResponse
import java.time.LocalDate
import java.time.LocalDateTime

class StoppKravServiceTest : ShouldSpec({

    context("ved innsending av en linje med valideringsfeil og en linje med ukjent sakref ") {
        val skeClientMock = mockk<SkeClient>()
        val databaseSericeMock = mockk<DatabaseService>()
        val stoppKravService = StoppKravService(skeClientMock, databaseSericeMock)
        justRun { databaseSericeMock.updateSentKravToDatabase(any()) }

        val httpResponseMock404 = mockk<HttpResponse>() {
            every { status.value } returns 404
            coEvery { body<FeilResponse>() } returns FeilResponse("innkrevingsoppdrag-eksisterer-ikke", "title", 404, "detail", "instance")
        }
        val httpResponseMock422 = mockk<HttpResponse>() {
            every { status.value } returns 422
            coEvery { body<FeilResponse>() } returns FeilResponse("type", "title", 422, "detail", "instance")
        }

        val kravListe = listOf(
            KravTable(
                111, "skeref123", "navref123", 0.0, LocalDate.now(), "12345678901", "20230112", "20233112", "KS KS", "navoldref123", "20240105", "0408", "0408", "T", "", 0.0, 0.0, LocalDate.now(), "", "KRAV_IKKE_SENDT", "STOPP_KRAV", "cor123", LocalDateTime.now(),
                LocalDateTime.now(),
                LocalDateTime.now()
            ),
            KravTable(
                111, "skeref123", "navref123", 0.0, LocalDate.now(), "12345678901", "20230112", "20233112", "KS KS", "navoldref123", "20240105", "0408", "0408", "T", "", 0.0, 0.0, LocalDate.now(), "", "KRAV_IKKE_SENDT", "STOPP_KRAV", "cor124", LocalDateTime.now(),
                LocalDateTime.now(),
                LocalDateTime.now()
            ),
        )

        coEvery { skeClientMock.stoppKrav(any(), any()) } returns httpResponseMock422 andThen httpResponseMock404
        should(" vi få 404 og 422") {
            val resultList = stoppKravService.sendAllStopKrav(kravListe).flatMap { it.entries }.map { it.value }

            resultList.filter { it.status == Status.FANT_IKKE_SAKSREF_404 }.size shouldBe 1
            resultList.filter { it.status == Status.VALIDERINGSFEIL_422 }.size shouldBe 1

        }
    }


})
