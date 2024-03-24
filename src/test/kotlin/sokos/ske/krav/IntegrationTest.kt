package sokos.ske.krav

import com.zaxxer.hikari.HikariDataSource
import io.kotest.core.annotation.Ignored
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.http.*
import io.mockk.mockk
import kotlinx.serialization.json.Json
import sokos.ske.krav.client.SkeClient
import sokos.ske.krav.database.PostgresDataSource
import sokos.ske.krav.database.RepositoryExtensions.toFeilmelding
import sokos.ske.krav.database.RepositoryExtensions.toKrav
import sokos.ske.krav.database.models.Status
import sokos.ske.krav.domain.ske.responses.FeilResponse
import sokos.ske.krav.security.MaskinportenAccessTokenClient
import sokos.ske.krav.service.*
import sokos.ske.krav.util.*
import sokos.ske.krav.util.MockHttpClientUtils.EndepunktType
import sokos.ske.krav.util.MockHttpClientUtils.MockRequestObj
import sokos.ske.krav.util.MockHttpClientUtils.Responses

@Ignored
internal class IntegrationTest : FunSpec({

    test("Når SkeService leser inn en fil skal kravene lagres i database"){}
    test("Etter at kravene lagres i database skal endringer og avskrivinger oppdateres med kravidentifikatorSKE fra database"){}

    test("Kravdata skal lagres i database etter å ha sendt nye krav til SKE") {
        val nyttKravKall        = MockRequestObj(Responses.nyttKravResponse(), EndepunktType.OPPRETT, HttpStatusCode.OK)
        val avskrivKravKall     = MockRequestObj(Responses.nyEndringResponse(), EndepunktType.AVSKRIVING, HttpStatusCode.OK)
        val endreRenterKall     = MockRequestObj(Responses.nyEndringResponse(), EndepunktType.ENDRE_RENTER, HttpStatusCode.OK)
        val endreHovedstolKall  = MockRequestObj(Responses.nyEndringResponse(), EndepunktType.ENDRE_HOVEDSTOL, HttpStatusCode.OK)
        val endreReferanseKall  = MockRequestObj(Responses.nyEndringResponse(), EndepunktType.ENDRE_REFERANSE, HttpStatusCode.OK)
        val mottaksstatusKall   = MockRequestObj(Responses.mottaksStatusResponse(), EndepunktType.MOTTAKSSTATUS, HttpStatusCode.OK)
             val httpClient =
            setUpMockHttpClient(
                listOf(nyttKravKall, avskrivKravKall, endreRenterKall, endreHovedstolKall, endreReferanseKall, mottaksstatusKall)
            )

        //hvordan få dette til å ikke gjøre noe!!!!! sendNewFilesToSke må være public??
        val mocks: Pair<SkeService, HikariDataSource> =
            setupMocks(listOf("FilMedBare10Linjer.txt"), this.testCase.name.testName, httpClient)


        mocks.first.handleNewKrav()

        mocks.second.connection.getAllKrav().size shouldBe 10
    }

    test("Kravdata skal lagres med type som beskriver hva slags krav det er"){
        val nyttKravKall        = MockRequestObj(Responses.nyttKravResponse(), EndepunktType.OPPRETT, HttpStatusCode.OK)
        val avstemmKall         = MockRequestObj(Responses.nyttKravResponse(), EndepunktType.AVSTEMMING, HttpStatusCode.OK)
        val avskrivKravKall     = MockRequestObj(Responses.nyEndringResponse(), EndepunktType.AVSKRIVING, HttpStatusCode.OK)
        val endreRenterKall     = MockRequestObj(Responses.nyEndringResponse(), EndepunktType.ENDRE_RENTER, HttpStatusCode.OK)
        val endreHovedstolKall  = MockRequestObj(Responses.nyEndringResponse(), EndepunktType.ENDRE_HOVEDSTOL, HttpStatusCode.OK)
        val endreReferanseKall  = MockRequestObj(Responses.nyEndringResponse(), EndepunktType.ENDRE_REFERANSE, HttpStatusCode.OK)
        val mottaksstatusKall   = MockRequestObj(Responses.mottaksStatusResponse(), EndepunktType.MOTTAKSSTATUS, HttpStatusCode.OK)

        val httpClient = setUpMockHttpClient(listOf(nyttKravKall, avskrivKravKall, endreRenterKall, endreHovedstolKall, endreReferanseKall, avstemmKall, mottaksstatusKall))
        val mocks: Pair<SkeService, HikariDataSource> = setupMocks(listOf("AltOkFil.txt"), this.testCase.name.testName, httpClient)

        mocks.first.handleNewKrav()
        val kravdata = mocks.second.connection.getAllKrav()

        kravdata.filter { it.kravtype == STOPP_KRAV }.size shouldBe 2
        kravdata.filter { it.kravtype == ENDRE_RENTER }.size shouldBe 2
        kravdata.filter { it.kravtype == ENDRE_HOVEDSTOL }.size shouldBe 2
        kravdata.filter { it.kravtype == NYTT_KRAV }.size shouldBe 97
    }

    test("Mottaksstatus skal oppdateres i database") {
        val tokenProvider = mockk<MaskinportenAccessTokenClient>(relaxed = true)
        val mottaksstatusKall = MockRequestObj(Responses.mottaksStatusResponse(status =  Status.RESKONTROFOERT.value), EndepunktType.MOTTAKSSTATUS, HttpStatusCode.OK)
        val httpClient = setUpMockHttpClient(listOf(mottaksstatusKall))

        val skeClient = SkeClient(skeEndpoint = "", client = httpClient, tokenProvider = tokenProvider)
        val dataSource = startContainer(this.testCase.name.testName, listOf("NyeKrav.sql"))
        val databaseService = DatabaseService(PostgresDataSource(dataSource))
        val statusService = StatusService(skeClient, databaseService)


        statusService.hentOgOppdaterMottaksStatus()
        val kravdata = dataSource.connection.getAllKrav()

        kravdata.filter { it.status == Status.RESKONTROFOERT.value }.size shouldBe 2
    }

    test("Når et krav feiler skal det lagres i feilmeldingtabell") {
        val nyttKravKall        = MockRequestObj(Responses.innkrevingsOppdragEksistererIkkeResponse(), EndepunktType.OPPRETT, HttpStatusCode.NotFound)
        val avskrivKravKall     = MockRequestObj(Responses.innkrevingsOppdragEksistererIkkeResponse(), EndepunktType.AVSKRIVING, HttpStatusCode.NotFound)
        val endreRenterKall     = MockRequestObj(Responses.innkrevingsOppdragEksistererIkkeResponse(), EndepunktType.ENDRE_RENTER, HttpStatusCode.NotFound)
        val endreHovedstolKall  = MockRequestObj(Responses.innkrevingsOppdragEksistererIkkeResponse(), EndepunktType.ENDRE_HOVEDSTOL, HttpStatusCode.NotFound)
        val endreReferanseKall  = MockRequestObj(Responses.innkrevingsOppdragEksistererIkkeResponse(), EndepunktType.ENDRE_REFERANSE, HttpStatusCode.NotFound)
        val mottaksstatusKall   = MockRequestObj(Responses.mottaksStatusResponse(), EndepunktType.MOTTAKSSTATUS, HttpStatusCode.OK)

        val httpClient =
            setUpMockHttpClient(
                listOf(nyttKravKall, avskrivKravKall, endreRenterKall, endreHovedstolKall, endreReferanseKall, mottaksstatusKall),
            )
        val mocks: Pair<SkeService, HikariDataSource> =
            setupMocks(listOf("FilMedBare10Linjer.txt"), this.testCase.name.testName, httpClient)


        mocks.first.handleNewKrav()
        val feilmeldinger = mocks.second.connection.prepareStatement("SELECT * FROM FEILMELDING").executeQuery().toFeilmelding()

        feilmeldinger.size shouldBe 10
        feilmeldinger.map { Json.decodeFromString<FeilResponse>(it.skeResponse).status == 404 }.size shouldBe 10

        val joinToString = feilmeldinger.joinToString("','") { it.corrId }.also(::println)
        val kravMedFeil =
            mocks.second.connection.prepareStatement(
                """select * from Krav where corr_id in ('$joinToString')""",
            ).executeQuery().toKrav()

        kravMedFeil.size shouldBe 10
        kravMedFeil.filter { it.status == Status.FANT_IKKE_SAKSREF_404.value }.size shouldBe 10
    }

    test("Hvis krav har status KRAV_IKKE_SENDT, IKKE_RESKONTROFORT_RESEND, ANNEN_SERVER_FEIL_500, UTILGJENGELIG_TJENESTE_503, eller INTERN_TJENERFEIL_500 så skal kravet resendes") {
        val ds = startContainer(this.testCase.name.testName, listOf("KravSomSkalResendes.sql"))

        ds.connection.getAllKrav().let { kravBefore ->
            kravBefore.filter { it.status == Status.KRAV_IKKE_SENDT.value }.size shouldBe 1
            kravBefore.filter { it.status == Status.IKKE_RESKONTROFORT_RESEND.value }.size shouldBe 3
            kravBefore.filter { it.status == Status.ANNEN_SERVER_FEIL_500.value }.size shouldBe 1
            kravBefore.filter { it.status == Status.UTILGJENGELIG_TJENESTE_503.value }.size shouldBe 1
            kravBefore.filter { it.status == Status.INTERN_TJENERFEIL_500.value }.size shouldBe 1
        }

        val nyttKravKall        = MockRequestObj(Responses.nyttKravResponse(), EndepunktType.OPPRETT, HttpStatusCode.OK)
        val avskrivKravKall     = MockRequestObj(Responses.nyEndringResponse(), EndepunktType.AVSKRIVING, HttpStatusCode.OK)
        val endreRenterKall     = MockRequestObj(Responses.nyEndringResponse(), EndepunktType.ENDRE_RENTER, HttpStatusCode.OK)
        val endreHovedstolKall  = MockRequestObj(Responses.nyEndringResponse(), EndepunktType.ENDRE_HOVEDSTOL, HttpStatusCode.OK)
        val endreReferanseKall  = MockRequestObj(Responses.nyEndringResponse(), EndepunktType.ENDRE_REFERANSE, HttpStatusCode.OK)

        val httpClient = setUpMockHttpClient(listOf(nyttKravKall, avskrivKravKall, endreRenterKall, endreHovedstolKall, endreReferanseKall))

        val skeService = setupSkeServiceMock(ds, httpClient)
        val feilListe = skeService.resendKrav()

        ds.connection.getAllKrav().let { kravBefore ->
            kravBefore.filter { it.status == Status.KRAV_IKKE_SENDT.value }.size shouldBe 0
            kravBefore.filter { it.status == Status.IKKE_RESKONTROFORT_RESEND.value }.size shouldBe 0
            kravBefore.filter { it.status == Status.ANNEN_SERVER_FEIL_500.value }.size shouldBe 0
            kravBefore.filter { it.status == Status.UTILGJENGELIG_TJENESTE_503.value }.size shouldBe 0
            kravBefore.filter { it.status == Status.INTERN_TJENERFEIL_500.value }.size shouldBe 0
        }

        feilListe.size shouldBe 0
    }

})
