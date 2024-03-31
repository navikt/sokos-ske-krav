package sokos.ske.krav.util

import com.zaxxer.hikari.HikariDataSource
import io.kotest.extensions.testcontainers.toDataSource
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.every
import io.mockk.mockk
import sokos.ske.krav.client.SkeClient
import sokos.ske.krav.database.PostgresDataSource
import sokos.ske.krav.database.RepositoryExtensions.toKrav
import sokos.ske.krav.database.models.KravTable
import sokos.ske.krav.domain.ske.responses.FeilResponse
import sokos.ske.krav.security.MaskinportenAccessTokenClient
import sokos.ske.krav.service.DatabaseService
import sokos.ske.krav.service.Directories
import sokos.ske.krav.service.EndreKravService
import sokos.ske.krav.service.FtpService
import sokos.ske.krav.service.OpprettKravService
import sokos.ske.krav.service.SkeService
import sokos.ske.krav.service.StatusService
import sokos.ske.krav.service.StoppKravService
import java.io.File
import java.net.URI
import java.sql.Connection


fun readFileFromFS(file: String): List<String> {
    val pn = URI(file)
    pn.normalize()
    return File(URI(file)).readLines()
}

fun String.asResource(): String = object {}.javaClass.classLoader.getResource(this)!!.toString()
fun String.asText(): String =
    object {}.javaClass.classLoader.getResourceAsStream(this)!!.bufferedReader().use { it.readText() }

fun startContainer(containerName: String, initScripts: List<String>): HikariDataSource {
    return TestContainer(containerName)
        .getContainer(initScripts)
        .toDataSource {
            maximumPoolSize = 8
            minimumIdle = 1
            isAutoCommit = false
        }
}

private val mockSkeClient = mockk<SkeClient> {
    coJustRun { getSkeKravident(any()) }
}

private val stoppServiceMock = mockk<StoppKravService> {
    coEvery { sendAllStopKrav(any()) } returns emptyList()
}

private val endreServiceMock = mockk<EndreKravService> {
    coEvery { sendAllEndreKrav(any()) } returns emptyList()
}

private val opprettServiceMock = mockk<OpprettKravService> {
    coEvery { sendAllOpprettKrav(any()) } returns emptyList()
}

private val statusServiceMock = mockk<StatusService> {
    coJustRun { hentOgOppdaterMottaksStatus() }
}

private val ftpServiceMock = FakeFtpService().setupMocks(Directories.INBOUND, emptyList())

fun setupSkeServiceMock(
    skeClient: SkeClient = mockSkeClient,
    stoppService: StoppKravService = stoppServiceMock,
    endreService: EndreKravService = endreServiceMock,
    opprettService: OpprettKravService = opprettServiceMock,
    statusService: StatusService = statusServiceMock,
    databaseService: DatabaseService,
    ftpService: FtpService = ftpServiceMock
) = SkeService(
    skeClient, stoppService, endreService, opprettService, statusService, databaseService, ftpService
)

fun setUpMockHttpClient(endepunktTyper: List<MockHttpClientUtils.MockRequestObj>) = MockHttpClient().getClient(endepunktTyper)

fun setupSkeServiceMockWithMockEngine(
    dataSource: HikariDataSource,
    httpClient: HttpClient,
    ftpFiler: List<String> = emptyList(),
    directory: Directories = Directories.INBOUND,

    ): SkeService {
    val tokenProvider = mockk<MaskinportenAccessTokenClient>(relaxed = true)

    val ftpService = FakeFtpService().setupMocks(directory, ftpFiler)
    val skeClient = SkeClient(skeEndpoint = "", client = httpClient, tokenProvider = tokenProvider)
    val databaseService = DatabaseService(PostgresDataSource(dataSource))
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
    )
}

fun setupMocksWithMockEngine(
    ftpFiler: List<String>,
    containerName: String,
    httpClient: HttpClient,
    initScripts: List<String> = emptyList(),
    directory: Directories = Directories.INBOUND,
): Pair<SkeService, HikariDataSource> {

    val dataSource = startContainer(containerName, initScripts)
    val skeServiceMock = setupSkeServiceMockWithMockEngine(dataSource, httpClient, ftpFiler, directory)

    return Pair(
        skeServiceMock,
        dataSource,
    )
}


fun mockFeilResponsCall(code: Int, feilResponseType: String = "") = mockk<HttpResponse>() {
    every { status.value } returns code
    coEvery { body<FeilResponse>().type } returns feilResponseType
}

fun Connection.getAllKrav(): List<KravTable> {
    return prepareStatement("""select * from krav""").executeQuery().toKrav()
}