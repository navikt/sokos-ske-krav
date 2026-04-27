package no.nav.sokos.ske.krav.util

import java.io.File
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
import no.nav.sokos.ske.krav.domain.Krav
import no.nav.sokos.ske.krav.repository.FeilmeldingRepository
import no.nav.sokos.ske.krav.repository.KravRepository
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

private val feilmeldingRepositoryMock =
    mockk<FeilmeldingRepository> {
        every { insertFeilmelding(any()) } returns Unit
    }

private val kravRepositoryMock =
    mockk<KravRepository> {
        every { getKravTableIdFromCorrelationId(any()) } returns 1L
    }

fun setupSkeServiceMock(
    skeClient: SkeClient = mockSkeClient,
    stoppKravService: StoppKravService = stoppServiceMock,
    endreKravService: EndreKravService = endreServiceMock,
    opprettKravService: OpprettKravService = opprettServiceMock,
    statusService: StatusService = statusServiceMock,
    databaseService: DatabaseService = dataSourceMock,
    ftpService: FtpService = ftpServiceMock,
    slackService: SlackService = SlackService(SlackClient(client = MockHttpClient.slackClient)),
    feilmeldingRepository: FeilmeldingRepository = feilmeldingRepositoryMock,
    kravRepository: KravRepository = kravRepositoryMock,
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
    feilmeldingRepository = feilmeldingRepository,
    kravRepository = kravRepository,
)

fun setupSkeServiceMockWithMockEngine(
    dataSource: HikariDataSource,
    httpClient: HttpClient,
    ftpService: FtpService,
    databaseService: DatabaseService,
    slackClient: SlackClient = SlackClient(client = MockHttpClient.slackClient),
    slackService: SlackService = SlackService(slackClient),
    feilmeldingRepository: FeilmeldingRepository,
    kravRepository: KravRepository,
): SkeService {
    val tokenProvider = mockk<MaskinportenAccessTokenProvider>(relaxed = true)
    val skeClient = SkeClient(skeEndpoint = "", client = httpClient, tokenProvider = tokenProvider)
    val endreKravService = EndreKravService(skeClient, databaseService)
    val opprettKravService = OpprettKravService(skeClient, databaseService)
    val statusService = StatusService(dataSource, skeClient, databaseService, slackService, feilmeldingRepository)
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
        feilmeldingRepository = feilmeldingRepository,
        kravRepository = kravRepository,
    )
}

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
