package sokos.ske.krav.service.integration

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode
import io.mockk.mockk
import sokos.ske.krav.client.SkeClient
import sokos.ske.krav.database.PostgresDataSource
import sokos.ske.krav.security.MaskinportenAccessTokenClient
import sokos.ske.krav.service.DatabaseService
import sokos.ske.krav.service.StatusService
import sokos.ske.krav.util.MockHttpClientUtils
import sokos.ske.krav.util.getAllKrav
import sokos.ske.krav.util.setUpMockHttpClient
import sokos.ske.krav.util.startContainer

internal class StatusServiceIntegrationTest : FunSpec ({

    test("hentOgOppdaterMottaksStatus skal hente alle krav som har status KRAV_SENDT eller MOTTATT_UNDERBEHANDLING fra db, hente mottaksstatus fra SKE, og lagre det i database"){
        val dataSource = startContainer(this.testCase.name.testName, listOf("KravSomSkalAvstemmes.sql"))
        val dbService =  DatabaseService(PostgresDataSource(dataSource))

        val mottaksStatusResponse = MockHttpClientUtils.Responses.mottaksStatusResponse()
        val valideringsFeilRespons = MockHttpClientUtils.Responses.emptyValideringsfeilResponse()

        val httpClient = setUpMockHttpClient(listOf(
            MockHttpClientUtils.MockRequestObj(mottaksStatusResponse, MockHttpClientUtils.EndepunktType.MOTTAKSSTATUS, HttpStatusCode.OK),
            MockHttpClientUtils.MockRequestObj(valideringsFeilRespons, MockHttpClientUtils.EndepunktType.HENT_VALIDERINGSFEIL, HttpStatusCode.OK)
        ))
        val skeClient = SkeClient(skeEndpoint = "", client = httpClient, tokenProvider = mockk<MaskinportenAccessTokenClient>(relaxed = true))

        val statusService = StatusService(skeClient, dbService)

        val allKravBeforeUpdate = dataSource.connection.use { con -> con.getAllKrav() }
        allKravBeforeUpdate.filter { it.status == "RESKONTROFOERT" }.size shouldBe 1
        
        statusService.hentOgOppdaterMottaksStatus()

       val allKravAfterUpdate = dataSource.connection.use { con -> con.getAllKrav() }
        allKravAfterUpdate.filter { it.status == "RESKONTROFOERT" }.size shouldBe 3

    }
    test("hentOgLagreValideringsFeil skal hente valideringsfeil fra SKE og lagre dem i DB"){
        val dataSource = startContainer(this.testCase.name.testName, listOf("KravSomSkalAvstemmes.sql"))
        val dbService =  DatabaseService(PostgresDataSource(dataSource))

        val mottaksStatusResponse =MockHttpClientUtils.Responses.mottaksStatusResponse(status="VALIDERINGSFEIL")
        val valideringsFeilRespons = MockHttpClientUtils.Responses.valideringsfeilResponse("420", "Feilmelding test" )

        val httpClient = setUpMockHttpClient(listOf(
            MockHttpClientUtils.MockRequestObj(mottaksStatusResponse, MockHttpClientUtils.EndepunktType.MOTTAKSSTATUS, HttpStatusCode.OK),
            MockHttpClientUtils.MockRequestObj(valideringsFeilRespons, MockHttpClientUtils.EndepunktType.HENT_VALIDERINGSFEIL, HttpStatusCode.OK)
        ))
        val skeClient = SkeClient(skeEndpoint = "", client = httpClient, tokenProvider = mockk<MaskinportenAccessTokenClient>(relaxed = true))

        val statusService = StatusService(skeClient, dbService)

        dbService.getAllFeilmeldinger().size shouldBe 0
        statusService.hentOgOppdaterMottaksStatus()

        val errorMessages = dbService.getAllFeilmeldinger()
        errorMessages.size shouldBe 2
        errorMessages.filter { it.error == "420" }.size shouldBe 2

    }


})