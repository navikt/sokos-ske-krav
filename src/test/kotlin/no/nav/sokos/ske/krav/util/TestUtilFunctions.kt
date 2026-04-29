package no.nav.sokos.ske.krav.util

import java.io.Reader
import java.sql.Connection

import kotlinx.io.Buffer

import com.zaxxer.hikari.HikariDataSource
import io.ktor.client.HttpClient
import io.ktor.client.call.HttpClientCall
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk

import no.nav.sokos.ske.krav.client.SkeClient
import no.nav.sokos.ske.krav.client.SlackClient
import no.nav.sokos.ske.krav.client.SlackService
import no.nav.sokos.ske.krav.copybook.KravLinje
import no.nav.sokos.ske.krav.domain.Feilmelding
import no.nav.sokos.ske.krav.domain.Krav
import no.nav.sokos.ske.krav.repository.RepositoryExtensions.withParameters
import no.nav.sokos.ske.krav.repository.toFeilmelding
import no.nav.sokos.ske.krav.repository.toKrav
import no.nav.sokos.ske.krav.security.MaskinportenAccessTokenProvider
import no.nav.sokos.ske.krav.service.DatabaseService
import no.nav.sokos.ske.krav.service.EndreKravService
import no.nav.sokos.ske.krav.service.FtpService
import no.nav.sokos.ske.krav.service.OpprettKravService
import no.nav.sokos.ske.krav.service.SkeService
import no.nav.sokos.ske.krav.service.StatusService
import no.nav.sokos.ske.krav.service.StoppKravService
import no.nav.sokos.ske.krav.util.http.MockHttpClient
import no.nav.sokos.ske.krav.util.http.MockResponse

object FtpTestUtil {
    fun fileAsString(fileName: String): String = fileAs(fileName, Reader::readText)

    fun getFileContent(filename: String): List<String> = fileAs("/FtpFiler/$filename", Reader::readLines)

    private fun <T> fileAs(
        fileName: String,
        func: Reader.() -> T,
    ): T =
        this::class.java
            .getResourceAsStream(fileName)!!
            .bufferedReader()
            .use { it.func() }
}

fun setupSkeServiceMock(
    skeClient: SkeClient = mockk { coJustRun { getSkeKravidentifikator(any()) } },
    stoppKravService: StoppKravService = mockk { coEvery { sendAllStoppKrav(any()) } returns emptyList() },
    endreKravService: EndreKravService = mockk { coEvery { sendAllEndreKrav(any()) } returns emptyList() },
    opprettKravService: OpprettKravService = mockk { coEvery { sendAllOpprettKrav(any()) } returns emptyList() },
    statusService: StatusService = mockk { coJustRun { getMottaksStatus() } },
    databaseService: DatabaseService =
        mockk {
            every { getAllUnsentKrav() } returns emptyList()
            every { getAllKravForResending() } returns emptyList()
            justRun { saveAllNewKrav(any<List<KravLinje>>(), "filnavn.txt") }
            every { getSkeKravidentifikator(any<String>()) } returns "foo"
        },
    ftpService: FtpService = mockk(),
    slackService: SlackService = SlackService(SlackClient(client = MockHttpClient.slackClient)),
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

fun setupSkeServiceMockWithMockEngine(
    dataSource: HikariDataSource,
    httpClient: HttpClient,
    ftpService: FtpService,
    databaseService: DatabaseService,
    slackClient: SlackClient = SlackClient(client = MockHttpClient.slackClient),
    slackService: SlackService = SlackService(slackClient),
): SkeService {
    val skeClient = skeClient(httpClient)
    return SkeService(
        dataSource = dataSource,
        skeClient = skeClient,
        stoppKravService = StoppKravService(skeClient, databaseService),
        endreKravService = EndreKravService(skeClient, databaseService),
        opprettKravService = OpprettKravService(skeClient, databaseService),
        statusService = StatusService(dataSource, skeClient, databaseService, slackService),
        databaseService = databaseService,
        ftpService = ftpService,
        slackService = slackService,
    )
}

fun skeClient(vararg responses: MockResponse): SkeClient = skeClient(MockHttpClient.client(*responses))

fun skeClient(httpClient: HttpClient): SkeClient = SkeClient(skeEndpoint = "", client = httpClient, tokenProvider = mockk<MaskinportenAccessTokenProvider>(relaxed = true))

fun mockHttpResponse(
    code: Int = 200,
    body: String = "",
): HttpResponse =
    mockk<HttpResponse>(relaxed = true) {
        every { status } returns HttpStatusCode.fromValue(code)
        every { call } returns
            mockk<HttpClientCall>(relaxed = true) {
                coEvery { bodyNullable(any()) } answers {
                    Buffer().also { it.write(body.toByteArray()) }
                }
            }
    }

fun Connection.getAllKrav(): List<Krav> = prepareStatement("""select * from krav""").executeQuery().toKrav()

fun Connection.getAllFeilmeldinger(): List<Feilmelding> = prepareStatement("SELECT * FROM feilmelding").executeQuery().toFeilmelding()

fun Connection.getKravForFeilmeldinger(feilmeldinger: List<Feilmelding>): List<Krav> =
    feilmeldinger.flatMap { feilmelding ->
        prepareStatement("""select * from krav where corr_id = ?""")
            .withParameters(feilmelding.corrId)
            .executeQuery()
            .toKrav()
    }
