package sokos.ske.krav.service.integration

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode
import io.mockk.mockk
import sokos.ske.krav.client.SkeClient
import sokos.ske.krav.database.PostgresDataSource
import sokos.ske.krav.database.models.KravTable
import sokos.ske.krav.security.MaskinportenAccessTokenClient
import sokos.ske.krav.service.DatabaseService
import sokos.ske.krav.service.OpprettKravService
import sokos.ske.krav.util.MockHttpClientUtils
import sokos.ske.krav.util.getAllKrav
import sokos.ske.krav.util.setUpMockHttpClient
import sokos.ske.krav.util.startContainer
import java.time.LocalDate
import java.time.LocalDateTime

internal class OpprettKravServiceIntegrationTest : FunSpec({

    test("Nye krav skal lagres i database med response fra skeclient") {
        val dataSource = startContainer(this.testCase.name.testName, listOf("NyeKrav.sql"))

        val kravidentifikatorSKE = "4321"
        val skeResponse = MockHttpClientUtils.Responses.nyttKravResponse(kravidentifikatorSKE)

        val httpClient = setUpMockHttpClient(listOf(MockHttpClientUtils.MockRequestObj(skeResponse, MockHttpClientUtils.EndepunktType.OPPRETT, HttpStatusCode.OK)))
        val skeClient = SkeClient(skeEndpoint = "", client = httpClient, tokenProvider = mockk<MaskinportenAccessTokenClient>(relaxed = true))

        val opprettService = OpprettKravService(skeClient, DatabaseService(PostgresDataSource(dataSource)))
        val kravListe = listOf(
            KravTable(
                111, "filnavn.txt", 1, "",  "1111-navsaksnr", 111.0, LocalDate.now(), "12345678901", "20231201", "20231231", "KS KS",
                "", "20240105", "0408", "0408", "T", "", 0.0, 0.0, LocalDate.now(),
                "", "KRAV_IKKE_SENDT", "NYTT_KRAV", "CORR111", LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now()
            ),
            KravTable(
                222, "filnavn.txt", 1, "", "2222-navsaksnr", 222.0, LocalDate.now(), "12345678901", "20231201", "20231231", "KS KS",
                "", "20240105", "0408", "0408", "T", "", 0.0, 0.0, LocalDate.now(),
                "", "KRAV_IKKE_SENDT", "NYTT_KRAV", "CORR222", LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now()
            ),
        )

        dataSource.connection.getAllKrav().size shouldBe 2

        opprettService.sendAllOpprettKrav(kravListe)

        val lagredeKrav = dataSource.connection.getAllKrav()
        lagredeKrav.size shouldBe 2

        lagredeKrav.filter { it.kravidentifikatorSKE == kravidentifikatorSKE }.size shouldBe 2
        lagredeKrav.filter { it.saksnummerNAV == "1111-navsaksnr"}.size shouldBe 1
        lagredeKrav.filter { it.saksnummerNAV == "2222-navsaksnr"}.size shouldBe 1
    }
})