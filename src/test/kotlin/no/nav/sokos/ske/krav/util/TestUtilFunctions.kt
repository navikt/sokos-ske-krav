package no.nav.sokos.ske.krav.util

import java.io.File
import java.io.Reader

import kotlinx.io.Buffer

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
import no.nav.sokos.ske.krav.repository.FeilmeldingRepository
import no.nav.sokos.ske.krav.repository.FilValideringsfeilRepository
import no.nav.sokos.ske.krav.repository.KravRepository
import no.nav.sokos.ske.krav.security.MaskinportenAccessTokenProvider
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

private val filValideringsfeilRepositoryMock = mockk<FilValideringsfeilRepository>()

private val feilmeldingRepositoryMock =
    mockk<FeilmeldingRepository> {
        justRun { insertFeilmelding(any()) }
    }

private val kravRepositoryMock =
    mockk<KravRepository> {
        every { getKravTableIdFromCorrelationId(any()) } returns 1L
        every { getAllUnsentKrav() } returns emptyList()
        every { getAllKravForResending() } returns emptyList()
        every { getSkeKravidentifikator(any<String>()) } returns "foo"
        justRun { insertAllNewKrav(any<List<KravLinje>>(), "filnavn.txt") }
    }

fun setupSkeServiceMock(
    skeClient: SkeClient = mockSkeClient,
    stoppKravService: StoppKravService = stoppServiceMock,
    endreKravService: EndreKravService = endreServiceMock,
    opprettKravService: OpprettKravService = opprettServiceMock,
    statusService: StatusService = statusServiceMock,
    ftpService: FtpService = ftpServiceMock,
    slackService: SlackService = SlackService(SlackClient(client = MockHttpClient.slackClient)),
    filValideringsfeilRepository: FilValideringsfeilRepository = filValideringsfeilRepositoryMock,
    feilmeldingRepository: FeilmeldingRepository = feilmeldingRepositoryMock,
    kravRepository: KravRepository = kravRepositoryMock,
) = SkeService(
    skeClient = skeClient,
    stoppKravService = stoppKravService,
    endreKravService = endreKravService,
    opprettKravService = opprettKravService,
    statusService = statusService,
    ftpService = ftpService,
    slackService = slackService,
    filValideringsfeilRepository = filValideringsfeilRepository,
    feilmeldingRepository = feilmeldingRepository,
    kravRepository = kravRepository,
)

fun setupSkeServiceMockWithMockEngine(
    httpClient: HttpClient,
    ftpService: FtpService,
    slackClient: SlackClient = SlackClient(client = MockHttpClient.slackClient),
    slackService: SlackService = SlackService(slackClient),
    filValideringsfeilRepository: FilValideringsfeilRepository,
    feilmeldingRepository: FeilmeldingRepository,
    kravRepository: KravRepository,
): SkeService {
    val tokenProvider = mockk<MaskinportenAccessTokenProvider>(relaxed = true)
    val skeClient = SkeClient(skeEndpoint = "", client = httpClient, tokenProvider = tokenProvider)
    val endreKravService = EndreKravService(skeClient)
    val opprettKravService = OpprettKravService(skeClient)
    val statusService = StatusService(skeClient, slackService, feilmeldingRepository, kravRepository)
    val stoppKravService = StoppKravService(skeClient)

    return SkeService(
        skeClient = skeClient,
        stoppKravService = stoppKravService,
        endreKravService = endreKravService,
        opprettKravService = opprettKravService,
        statusService = statusService,
        ftpService = ftpService,
        slackService = slackService,
        filValideringsfeilRepository = filValideringsfeilRepository,
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
