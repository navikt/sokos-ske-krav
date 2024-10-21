package sokos.ske.krav.util

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import sokos.ske.krav.client.SkeClient
import sokos.ske.krav.client.SlackClient
import sokos.ske.krav.database.models.KravTable
import sokos.ske.krav.domain.nav.KravLinje
import sokos.ske.krav.domain.ske.responses.FeilResponse
import sokos.ske.krav.security.MaskinportenAccessTokenClient
import sokos.ske.krav.service.DatabaseService
import sokos.ske.krav.service.EndreKravService
import sokos.ske.krav.service.FtpService
import sokos.ske.krav.service.OpprettKravService
import sokos.ske.krav.service.SkeService
import sokos.ske.krav.service.StatusService
import sokos.ske.krav.service.StoppKravService
import toKrav
import java.io.Reader
import java.sql.Connection

object FtpTestUtil {
    fun fileAsString(fileName: String): String = fileAs(fileName, Reader::readText)

    fun fileAsList(fileName: String): List<String> = fileAs(fileName, Reader::readLines)

    private fun <T> fileAs(
        fileName: String,
        func: Reader.() -> T,
    ): T =
        this::class.java
            .getResourceAsStream(fileName)!!
            .bufferedReader()
            .use { it.func() }
}

private val mockSkeClient =
    mockk<SkeClient> {
        coJustRun { getSkeKravidentifikator(any()) }
    }

private val stoppServiceMock =
    mockk<StoppKravService> {
        coEvery { sendAllStoppKrav(any()) } returns emptyList()
    }

private val endreServiceMock =
    mockk<EndreKravService> {
        coEvery { sendAllEndreKrav(any()) } returns emptyList()
    }

private val opprettServiceMock =
    mockk<OpprettKravService> {
        coEvery { sendAllOpprettKrav(any()) } returns emptyList()
    }

private val statusServiceMock =
    mockk<StatusService> {
        coJustRun { hentOgOppdaterMottaksStatus() }
    }

private val ftpServiceMock = mockk<FtpService>()
private val dataSourceMock =
    mockk<DatabaseService> {
        every { getAllUnsentKrav() } returns emptyList()
        every { getAllKravForResending() } returns emptyList()
        justRun { saveAllNewKrav(any<List<KravLinje>>(), "filnavn.txt") }
        every { getSkeKravidentifikator(any<String>()) } returns "foo"
    }

fun setupSkeServiceMock(
    skeClient: SkeClient = mockSkeClient,
    stoppService: StoppKravService = stoppServiceMock,
    endreService: EndreKravService = endreServiceMock,
    opprettService: OpprettKravService = opprettServiceMock,
    statusService: StatusService = statusServiceMock,
    databaseService: DatabaseService = dataSourceMock,
    ftpService: FtpService = ftpServiceMock,
    slackClient: SlackClient = SlackClient(client = MockHttpClient().getSlackClient()),
) = SkeService(
    skeClient,
    stoppService,
    endreService,
    opprettService,
    statusService,
    databaseService,
    ftpService,
    slackClient,
)

fun setUpMockHttpClient(endepunktTyper: List<MockHttpClientUtils.MockRequestObj>) = MockHttpClient().getClient(endepunktTyper)

fun setupSkeServiceMockWithMockEngine(
    httpClient: HttpClient,
    ftpService: FtpService,
    databaseService: DatabaseService,
): SkeService {
    val tokenProvider = mockk<MaskinportenAccessTokenClient>(relaxed = true)

    val skeClient = SkeClient(skeEndpoint = "", client = httpClient, tokenProvider = tokenProvider)
    val endreKravService = EndreKravService(skeClient, databaseService)
    val opprettKravService = OpprettKravService(skeClient, databaseService)
    val statusService = StatusService(skeClient, databaseService)
    val stoppKravService = StoppKravService(skeClient, databaseService)
    return SkeService(
        skeClient,
        stoppKravService,
        endreKravService,
        opprettKravService,
        statusService,
        databaseService,
        ftpService,
        SlackClient(client = MockHttpClient().getSlackClient()),
    )
}

fun mockHttpResponse(
    code: Int,
    feilResponseType: String = "",
) = mockk<HttpResponse> {
    every { status.value } returns code
    coEvery { body<FeilResponse>().type } returns feilResponseType
}

fun Connection.getAllKrav(): List<KravTable> = prepareStatement("""select * from krav""").executeQuery().toKrav()
