package sokos.ske.krav.util

import com.zaxxer.hikari.HikariDataSource
import io.kotest.extensions.testcontainers.toDataSource
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.statement.*
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import sokos.ske.krav.client.SkeClient
import sokos.ske.krav.database.PostgresDataSource
import sokos.ske.krav.database.RepositoryExtensions.toKrav
import sokos.ske.krav.database.models.KravTable
import sokos.ske.krav.domain.ske.responses.FeilResponse
import sokos.ske.krav.security.MaskinportenAccessTokenClient
import sokos.ske.krav.service.*
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

fun setUpMockHttpClient(endepunktTyper: List<MockHttpClientUtils.MockRequestObj>) = MockHttpClient().getClient(endepunktTyper)

fun setupMocks(
    ftpFiler: List<String>,
    containerName: String,
    httpClient: HttpClient,
    initScripts: List<String> = emptyList(),
    directory: Directories = Directories.INBOUND,
): Pair<SkeService, HikariDataSource> {
    val tokenProvider = mockk<MaskinportenAccessTokenClient>(relaxed = true)

    val ftpService = FakeFtpService().setupMocks(directory, ftpFiler)

    val skeClient = SkeClient(skeEndpoint = "", client = httpClient, tokenProvider = tokenProvider)
    val dataSource = startContainer(containerName, initScripts)
    val databaseService = DatabaseService(PostgresDataSource(dataSource))
    val endreKravService = EndreKravService(skeClient, databaseService)
    val opprettKravService = OpprettKravService(skeClient, databaseService)
    val statusService = StatusService(skeClient, databaseService)
    val alarmService = AlarmService()
    val stoppKravService = StoppKravService(skeClient, databaseService)

    return Pair(
        SkeService(
            skeClient,
            stoppKravService,
            endreKravService,
            opprettKravService,
            statusService,
            alarmService,
            databaseService,
            ftpService,
        ),
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