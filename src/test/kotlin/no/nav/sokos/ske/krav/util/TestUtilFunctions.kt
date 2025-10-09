package no.nav.sokos.ske.krav.util

import java.io.File
import java.io.Reader
import java.sql.Connection

import com.zaxxer.hikari.HikariDataSource
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk

import no.nav.sokos.ske.krav.client.SkeClient
import no.nav.sokos.ske.krav.client.SlackClient
import no.nav.sokos.ske.krav.client.SlackService
import no.nav.sokos.ske.krav.domain.Krav
import no.nav.sokos.ske.krav.dto.nav.KravLinje
import no.nav.sokos.ske.krav.dto.ske.responses.FeilResponse
import no.nav.sokos.ske.krav.repository.toKrav
import no.nav.sokos.ske.krav.security.MaskinportenAccessTokenProvider
import no.nav.sokos.ske.krav.service.DatabaseService
import no.nav.sokos.ske.krav.service.EndreKravService
import no.nav.sokos.ske.krav.service.FtpService
import no.nav.sokos.ske.krav.service.OpprettKravService
import no.nav.sokos.ske.krav.service.SkeService
import no.nav.sokos.ske.krav.service.StatusService
import no.nav.sokos.ske.krav.service.StoppKravService

object FtpTestUtil {
    fun fileAsString(fileName: String): String = fileAs(fileName, Reader::readText)

    fun getFileContent(filename: String) = fileAs("${File.separator}FtpFiler${File.separator}/$filename", Reader::readLines)

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
        coJustRun { getMottaksStatus() }
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
    stoppKravService: StoppKravService = stoppServiceMock,
    endreKravService: EndreKravService = endreServiceMock,
    opprettKravService: OpprettKravService = opprettServiceMock,
    statusService: StatusService = statusServiceMock,
    databaseService: DatabaseService = dataSourceMock,
    ftpService: FtpService = ftpServiceMock,
    slackService: SlackService = SlackService(SlackClient(client = MockHttpClient().getSlackClient())),
) = SkeService(
    dataSource = mockk<HikariDataSource>(),
    skeClient = skeClient,
    stoppKravService = stoppKravService,
    endreKravService = endreKravService,
    opprettKravService = opprettKravService,
    statusService = statusService,
    databaseService = databaseService,
    ftpService = ftpService,
    slackService = slackService,
)

fun setUpMockHttpClient(endepunktTyper: List<MockHttpClientUtils.MockRequestObj>) = MockHttpClient().getClient(endepunktTyper)

fun setupSkeServiceMockWithMockEngine(
    dataSource: HikariDataSource,
    httpClient: HttpClient,
    ftpService: FtpService,
    databaseService: DatabaseService,
): SkeService {
    val tokenProvider = mockk<MaskinportenAccessTokenProvider>(relaxed = true)
    val slackClient = SlackClient(client = MockHttpClient().getSlackClient())
    val slackService = SlackService(slackClient)
    val skeClient = SkeClient(skeEndpoint = "", client = httpClient, tokenProvider = tokenProvider)
    val endreKravService = EndreKravService(skeClient, databaseService)
    val opprettKravService = OpprettKravService(skeClient, databaseService)
    val statusService = StatusService(dataSource, skeClient, databaseService, slackService)
    val stoppKravService = StoppKravService(skeClient, databaseService)

    return SkeService(
        dataSource = dataSource,
        skeClient = skeClient,
        stoppKravService = stoppKravService,
        endreKravService = endreKravService,
        opprettKravService = opprettKravService,
        statusService = statusService,
        databaseService = databaseService,
        ftpService = ftpService,
        slackService = slackService,
    )
}

fun mockHttpResponse(
    code: Int,
    feilResponseType: String = "",
) = mockk<HttpResponse> {
    every { status.value } returns code
    coEvery { body<FeilResponse>().type } returns feilResponseType
}

fun Connection.getAllKrav(): List<Krav> = prepareStatement("""select * from krav""").executeQuery().toKrav()
