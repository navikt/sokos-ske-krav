package sokos.ske.krav.service

import com.zaxxer.hikari.HikariDataSource
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.testcontainers.toDataSource
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.http.HttpStatusCode
import io.mockk.mockk
import kotlinx.serialization.json.Json
import sokos.ske.krav.client.SkeClient
import sokos.ske.krav.database.PostgresDataSource
import sokos.ske.krav.database.Repository.getAllKrav
import sokos.ske.krav.database.RepositoryExtensions.toFeilmelding
import sokos.ske.krav.database.RepositoryExtensions.toKrav
import sokos.ske.krav.database.models.Status
import sokos.ske.krav.domain.ske.responses.FeilResponse
import sokos.ske.krav.security.MaskinportenAccessTokenClient
import sokos.ske.krav.util.FakeFtpService
import sokos.ske.krav.util.MockHttpClient
import sokos.ske.krav.util.MockHttpClientUtils.EndepunktType
import sokos.ske.krav.util.MockHttpClientUtils.MockRequestObj
import sokos.ske.krav.util.MockHttpClientUtils.Responses
import sokos.ske.krav.util.MockHttpClientUtils.generateUrls
import sokos.ske.krav.util.TestContainer

internal class SkeServiceTest : FunSpec({
    fun startContainer(containerName: String, initScripts: List<String>): HikariDataSource {
        return TestContainer(containerName)
            .getContainer(initScripts, reusable = false, loadFlyway = true)
            .toDataSource {
                maximumPoolSize = 8
                minimumIdle = 4
                isAutoCommit = false
            }
    }

    fun setUpMockHttpClient(endepunktTyper: List<MockRequestObj>) = MockHttpClient().getClient(endepunktTyper)

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
        val alarmService = AlarmService()
        val stoppKravService = StoppKravService(skeClient, databaseService)

        return Pair(
            SkeService(
                skeClient,
                stoppKravService,
                endreKravService,
                opprettKravService,
                alarmService,
                databaseService,
                ftpService,
            ),
            dataSource,
        )
    }

    test("Kravdata skal lagres i database etter å ha sendt nye krav til SKE") {

        println("Oppretter mockobjekter")
        val nyttKravKall        = MockRequestObj(Responses.nyttKravResponse("1234"), listOf(EndepunktType.OPPRETT.url), HttpStatusCode.OK)
        val avskrivKravKall     = MockRequestObj("", generateUrls(EndepunktType.AVSKRIVING.url), HttpStatusCode.OK)
        val endreRenterKall     = MockRequestObj(Responses.endringResponse(), generateUrls(EndepunktType.ENDRE_RENTER.url), HttpStatusCode.OK)
        val endreHovedstolKall  = MockRequestObj(Responses.endringResponse(), generateUrls(EndepunktType.ENDRE_HOVEDSTOL.url), HttpStatusCode.OK)
        val endreReferanseKall  = MockRequestObj(Responses.endringResponse(), generateUrls(EndepunktType.ENDRE_REFERANSE.url), HttpStatusCode.OK)

        val httpClient =
            setUpMockHttpClient(
                listOf(nyttKravKall, avskrivKravKall, endreRenterKall, endreHovedstolKall, endreReferanseKall)
            )
        val mocks: Pair<SkeService, HikariDataSource> =
            setupMocks(listOf("FilMedBare10Linjer.txt"), this.testCase.name.testName, httpClient)

        mocks.first.sendNewFilesToSKE()

        val rs =
            mocks.second.connection.prepareStatement(
                """select count(*) as a from krav""".trimIndent(),
            ).executeQuery()
        rs.next()
        val kravdata = rs.getInt("a")

        kravdata shouldBe 10
    }

    test("Kravdata skal lagres med type som beskriver hva slags krav det er") {
        val nyttKravKall        = MockRequestObj(Responses.nyttKravResponse("1234"), listOf(EndepunktType.OPPRETT.url), HttpStatusCode.OK)
        val avskrivKravKall     = MockRequestObj(Responses.endringResponse(), generateUrls(EndepunktType.AVSKRIVING.url), HttpStatusCode.OK)
        val endreRenterKall     = MockRequestObj(Responses.endringResponse(), generateUrls(EndepunktType.ENDRE_RENTER.url), HttpStatusCode.OK)
        val endreHovedstolKall  = MockRequestObj(Responses.endringResponse(), generateUrls(EndepunktType.ENDRE_HOVEDSTOL.url), HttpStatusCode.OK)
        val endreReferanseKall  = MockRequestObj(Responses.endringResponse(), generateUrls(EndepunktType.ENDRE_REFERANSE.url), HttpStatusCode.OK)

        val httpClient = setUpMockHttpClient(listOf(nyttKravKall, avskrivKravKall, endreRenterKall, endreHovedstolKall, endreReferanseKall))
        val mocks: Pair<SkeService, HikariDataSource> = setupMocks(listOf("AltOkFil.txt"), this.testCase.name.testName, httpClient)

        mocks.first.sendNewFilesToSKE()
        val kravdata = mocks.second.connection.getAllKrav()
        val kravSet = kravdata.toSet()

        println("Kravsett størrelse: ${kravSet.size}")

        val endringsMap =
            kravdata.filter {
                it.kravtype == ENDRE_RENTER || it.kravtype == ENDRE_HOVEDSTOL
            }.toSet().groupBy { it.saksnummerSKE + it.saksnummerNAV }

        endringsMap.forEach {
            println("${it.key} : ${it.value.size}")
            it.value.forEach(::println)
        }

        kravdata.filter { it.kravtype == STOPP_KRAV }.size shouldBe 2
        kravdata.filter { it.kravtype == ENDRE_RENTER }.size shouldBe 2
        kravdata.filter { it.kravtype == ENDRE_HOVEDSTOL }.size shouldBe 2
        kravdata.filter { it.kravtype == NYTT_KRAV }.size shouldBe 97
    }

    test("Mottaksstatus skal oppdateres i database") {
        val mottaksstatusKall = MockRequestObj(Responses.mottaksStatusResponse(status =  Status.RESKONTROFOERT.value), generateUrls(EndepunktType.MOTTAKSSTATUS.url), HttpStatusCode.OK)

        val httpClient = setUpMockHttpClient(listOf(mottaksstatusKall))
        val mocks: Pair<SkeService, HikariDataSource> =
            setupMocks(listOf("AltOkFil.txt"), this.testCase.name.testName, httpClient, listOf("NyeKrav.sql"))

        mocks.first.hentOgOppdaterMottaksStatus()
        val kravdata = mocks.second.connection.getAllKrav()

        kravdata.filter { it.status == Status.RESKONTROFOERT.value }.size shouldBe 2
    }

    test("Når et krav feiler skal det lagres i feilmeldingtabell") {
        val nyttKravKall        = MockRequestObj(Responses.innkrevingsOppdragEksistererIkkeResponse(), listOf(EndepunktType.OPPRETT.url), HttpStatusCode.NotFound)
        val avskrivKravKall     = MockRequestObj(Responses.innkrevingsOppdragEksistererIkkeResponse(), generateUrls(EndepunktType.AVSKRIVING.url), HttpStatusCode.NotFound)
        val endreRenterKall     = MockRequestObj(Responses.innkrevingsOppdragEksistererIkkeResponse(), generateUrls(EndepunktType.ENDRE_RENTER.url), HttpStatusCode.NotFound)
        val endreHovedstolKall  = MockRequestObj(Responses.innkrevingsOppdragEksistererIkkeResponse(), generateUrls(EndepunktType.ENDRE_HOVEDSTOL.url), HttpStatusCode.NotFound)
        val endreReferanseKall  = MockRequestObj(Responses.innkrevingsOppdragEksistererIkkeResponse(), generateUrls(EndepunktType.ENDRE_REFERANSE.url), HttpStatusCode.NotFound)

        val httpClient =
            setUpMockHttpClient(
                listOf(nyttKravKall, avskrivKravKall, endreRenterKall, endreHovedstolKall, endreReferanseKall),
            )
        val mocks: Pair<SkeService, HikariDataSource> =
            setupMocks(listOf("FilMedBare10Linjer.txt"), this.testCase.name.testName, httpClient)

        println("type: ${Json.decodeFromString<FeilResponse>(nyttKravKall.response).type}")
        mocks.first.sendNewFilesToSKE()
        val feilmeldinger = mocks.second.connection.prepareStatement("SELECT * FROM FEILMELDING").executeQuery().toFeilmelding()

        feilmeldinger.size shouldBe 10
        feilmeldinger.map { Json.decodeFromString<FeilResponse>(it.skeResponse).status == 404 }.size shouldBe 10

        val joinToString = feilmeldinger.joinToString("','") { it.corrId }.also(::println)
        val kravMedFeil =
            mocks.second.connection.prepareStatement(
                """select * from Krav where corr_id in ('$joinToString')""",
            ).executeQuery().toKrav()

        kravMedFeil.size shouldBe 10
        kravMedFeil.filter { it.status == Status.FANT_IKKE_SAKSREF.value }.size shouldBe 10
    }

    test("Resending av krav ") {

        val nyttKravKall        = MockRequestObj(Responses.innkrevingsOppdragEksistererFraFor(), listOf(EndepunktType.OPPRETT.url), HttpStatusCode.UnprocessableEntity)
        val avskrivKravKall     = MockRequestObj(Responses.innkrevingsOppdragHarUgyldigTilstandResponse(), generateUrls(EndepunktType.AVSKRIVING.url), HttpStatusCode.Conflict)

        val endreRenterKall     = MockRequestObj(Responses.innkrevingsOppdragErIkkeReskontrofortResponse(), generateUrls(EndepunktType.ENDRE_RENTER.url), HttpStatusCode.Conflict)
        val endreHovedstolKall  = MockRequestObj(Responses.innkrevingsOppdragErIkkeReskontrofortResponse(), generateUrls(EndepunktType.ENDRE_HOVEDSTOL.url), HttpStatusCode.Conflict)
        val endreReferanseKall  = MockRequestObj(Responses.innkrevingsOppdragErIkkeReskontrofortResponse(), generateUrls(EndepunktType.ENDRE_REFERANSE.url), HttpStatusCode.Conflict)

        val httpClient = setUpMockHttpClient(listOf(nyttKravKall, avskrivKravKall, endreRenterKall, endreHovedstolKall, endreReferanseKall))

        val mocks: Pair<SkeService, HikariDataSource> = setupMocks(emptyList(), this.testCase.name.testName, httpClient, initScripts =  listOf("KravSomSkalResendes.sql"))

        val kravBefore = mocks.second.connection.prepareStatement("""select * from krav""").executeQuery().toKrav()
        kravBefore.filter { it.status == Status.IKKE_RESKONTROFORT_RESEND.value }.size shouldBe 3
        kravBefore.filter { it.status == Status.KRAV_IKKE_SENDT.value }.size shouldBe 1
        kravBefore.filter { it.status == Status.KRAV_SENDT.value }.size shouldBe 1

        mocks.first.resendIkkeReskontroforteKrav()

        val kravAfter = mocks.second.connection.prepareStatement("""select * from krav""").executeQuery().toKrav()

        kravAfter.forEach { println(it.status) }
        kravAfter.filter { it.status == Status.IKKE_RESKONTROFORT_RESEND.value }.size shouldBe 2
        kravAfter.filter { it.status == Status.KRAV_IKKE_SENDT.value }.size shouldBe 0
        kravAfter.filter { it.status == Status.ANNEN_KONFLIKT.value }.size shouldBe 1
        kravAfter.filter { it.status == Status.VALIDERINGSFEIL.value }.size shouldBe 1
        kravAfter.filter { it.status == Status.KRAV_SENDT.value }.size shouldBe 1
    }
})
