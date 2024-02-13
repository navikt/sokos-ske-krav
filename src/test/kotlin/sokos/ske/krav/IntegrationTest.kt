package sokos.ske.krav

import com.zaxxer.hikari.HikariDataSource
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.testcontainers.toDataSource
import io.kotest.matchers.shouldBe
import io.ktor.client.statement.HttpResponse
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import sokos.ske.krav.client.SkeClient
import sokos.ske.krav.database.Repository.getAllValidationErrors
import sokos.ske.krav.database.Repository.getAllKrav
import sokos.ske.krav.database.Repository.getAllKravForStatusCheck
import sokos.ske.krav.database.Repository.getSkeKravIdent
import sokos.ske.krav.database.Repository.insertNewKobling
import sokos.ske.krav.database.Repository.insertNewKrav
import sokos.ske.krav.database.Repository.saveValidationError
import sokos.ske.krav.database.Repository.updateStatus
import sokos.ske.krav.database.RepositoryExtensions.useAndHandleErrors
import sokos.ske.krav.database.models.FeilmeldingTable
import sokos.ske.krav.domain.nav.KravLinje
import sokos.ske.krav.database.models.Status
import sokos.ske.krav.domain.ske.responses.MottaksStatusResponse
import sokos.ske.krav.domain.ske.responses.ValideringsFeilResponse
import sokos.ske.krav.security.MaskinportenAccessTokenClient
import sokos.ske.krav.service.*
import sokos.ske.krav.util.FakeFtpService
import sokos.ske.krav.util.MockHttpClient
import sokos.ske.krav.util.TestContainer
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.LocalDate
import java.time.LocalDateTime

internal class IntegrationTest : FunSpec({
    val tokenProvider = mockk<MaskinportenAccessTokenClient>(relaxed = true)
    val testContainer = TestContainer("IntegrationTest-TestSendNyeKrav")
    val container = testContainer.getContainer(listOf("KravMedEndringer.sql"), reusable = true, loadFlyway = true)

    val ds = container.toDataSource {
        maximumPoolSize = 8
        minimumIdle = 4
        isAutoCommit = false
    }

    afterSpec {
        ds.close()
        TestContainer().stopAnyRunningContainer()
    }

    test("Kravdata skal lagres i database etter å ha sendt nye krav til SKE") {
        val httpClient = MockHttpClient(kravident = "1234").getClient()
        val skeClient = SkeClient(skeEndpoint = "", client = httpClient, tokenProvider = tokenProvider)
        val mockkKravService = mockKravService(ds)
        
        val fakeFtpService = FakeFtpService()
        val ftpService = fakeFtpService.setupMocks(Directories.INBOUND, listOf("AltOkFil.txt"))

        val skeService = SkeService(skeClient, mockkKravService, ftpService)

        skeService.sendNewFilesToSKE()

        val kravdata = ds.connection.use {
            it.getAllKrav()
        }
        kravdata.size shouldBe 103

        kravdata.filter { it.kravtype == STOPP_KRAV }.size shouldBe 2
        kravdata.filter { it.kravtype == ENDRE_RENTER }.size shouldBe 3
        kravdata.filter { it.kravtype == ENDRE_HOVEDSTOL }.size shouldBe 1
        kravdata.filter { it.kravtype == NYTT_KRAV }.size shouldBe 97
        kravdata.filter { it.kravtype == NYTT_KRAV && it.saksnummerSKE == "1234" }.size shouldBe 97

        httpClient.close()
        fakeFtpService.close()
    }

    test("Mottaksstatus skal oppdateres i database") {

        val httpClient = MockHttpClient(kravident = "1234").getClient()
        val skeClient = SkeClient(skeEndpoint = "", client = httpClient, tokenProvider = tokenProvider)

        val mockkKravService = mockKravService(ds)
        val skeService = SkeService(skeClient, mockkKravService, mockk<FtpService>())

        skeService.hentOgOppdaterMottaksStatus()

        val kravdata = ds.connection.use {
            it.getAllKrav()
        }

        println(kravdata)
        kravdata.filter { it.status == Status.RESKONTROFOERT.value }.size shouldBe 103

        httpClient.close()
    }

    data class ValideringFraDB(
        val kravidentifikatorSKE: String,
        val error: String,
        val melding: String,
        val dato: Timestamp,
    )

    test("Test hent valideringsfeil") {
        val iderForValideringsFeil = listOf("23", "54", "87")
        val httpClient = MockHttpClient(kravident = "1234", iderForValideringsFeil = iderForValideringsFeil).getClient()

        val skeClient = SkeClient(skeEndpoint = "", client = httpClient, tokenProvider = tokenProvider)

        val mockkKravService = mockKravService(ds)
        val skeService = SkeService(skeClient, mockkKravService, mockk<FtpService>())

        ds.connection.use { con ->
            iderForValideringsFeil.forEach { id ->
                con.prepareStatement(
                    """
                update krav
                set 
                kravidentifikator_ske = $id,
                status = '${Status.VALIDERINGSFEIL.value}'
                where id = $id;
                    """.trimIndent(),
                ).executeUpdate()
                con.commit()
            }
        }

        skeService.hentValideringsfeil().size shouldBe 3

        val valideringsFeil = mutableListOf<ValideringFraDB>()
        ds.connection.use { con ->
            val rs: ResultSet = con.prepareStatement(
                """select * from validering where kravidentifikator_ske in('23', '54', '87')""".trimIndent(),
            ).executeQuery()
            while (rs.next()) {
                valideringsFeil.add(
                    ValideringFraDB(
                        rs.getString("kravidentifikator_ske"),
                        rs.getString("error"),
                        rs.getString("melding"),
                        rs.getTimestamp("dato"),
                    ),
                )
            }
        }
        valideringsFeil.size shouldBe 3

        valideringsFeil.forEach {
            it.error shouldBe "feil"
            it.melding shouldBe "melding"
            it.dato.toString() shouldBe "${LocalDate.now()} 00:00:00.0"
        }
        httpClient.close()
    }
})

fun mockKravService(ds: HikariDataSource): DatabaseService =
    mockk<DatabaseService>(relaxUnitFun = true, relaxed=true) {
    every { getSkeKravident(any<String>()) } answers {
        ds.connection.useAndHandleErrors { con ->
            con.getSkeKravIdent(firstArg<String>())
        }
    }

    every { insertNewKobling(any<String>(), any<String>()) } answers {
        ds.connection.useAndHandleErrors { con ->
            con.insertNewKobling(firstArg<String>(), secondArg<String>())
        }
    }


    every {
        insertNewKrav(
            any<String>(),
            any<String>(),
            any<KravLinje>(),
            any<String>(),
            any<String>(),
        )
    } answers {
        ds.connection.useAndHandleErrors { con ->
            con.insertNewKrav(
                arg<String>(0),
                arg<String>(1),
                arg<KravLinje>(2),
                arg<String>(3),
                arg<String>(4),
            )
        }
    }
    every { getAlleKravMedValideringsfeil() } answers {
        ds.connection.useAndHandleErrors { con ->
            con.getAllValidationErrors()
        }
    }
    every { saveValideringsfeil(any<ValideringsFeilResponse>(), any<String>()) } answers {
        ds.connection.useAndHandleErrors { con ->
            con.saveValidationError(firstArg<ValideringsFeilResponse>(), secondArg<String>())
        }
    }

    every { hentAlleKravSomIkkeErReskotrofort() } answers {
        ds.connection.useAndHandleErrors { con ->
            con.getAllKravForStatusCheck()
        }
    }
    every { updateStatus(any<MottaksStatusResponse>()) } answers {
        ds.connection.useAndHandleErrors { con ->
            con.updateStatus(firstArg<MottaksStatusResponse>())
        }
    }

        coEvery { saveSentKravToDatabase(any<Map<String, HttpResponse>>(), any<KravLinje>(), any<String>(), any<String>() ) }  answers{
            insertNewKrav(arg(2), arg(3), arg(1), arg<Map<String,HttpResponse>>(0).keys.first(), "STATUS")
        }
        coEvery { saveErrorMessageToDatabase(any<String>(), any<HttpResponse>(), any<KravLinje>(), any<String>() ) } answers {
            val feilmelding = FeilmeldingTable(
                0L,
                1L,
                 arg<KravLinje>(2).saksNummer,
                arg<String>(3),
                 "STATUS",
                "DETAIL",
                "REQUEST",
                "RESPONSE",
                LocalDateTime.now()
            )
             saveFeilmelding(feilmelding)

        }

    }
