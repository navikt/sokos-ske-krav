package sokos.ske.krav

import com.zaxxer.hikari.HikariDataSource
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.testcontainers.toDataSource
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode
import io.mockk.every
import io.mockk.mockk
import sokos.ske.krav.client.SkeClient
import sokos.ske.krav.database.Repository.hentAlleKravData
import sokos.ske.krav.database.Repository.hentAlleKravMedValideringsfeil
import sokos.ske.krav.database.Repository.hentAlleKravSomIkkeErReskotrofort
import sokos.ske.krav.database.Repository.hentSkeKravIdent
import sokos.ske.krav.database.Repository.lagreNyKobling
import sokos.ske.krav.database.Repository.lagreNyttKrav
import sokos.ske.krav.database.Repository.lagreValideringsfeil
import sokos.ske.krav.database.Repository.oppdaterStatus
import sokos.ske.krav.database.RepositoryExtensions.useAndHandleErrors
import sokos.ske.krav.domain.nav.KravLinje
import sokos.ske.krav.domain.ske.responses.MottaksStatusResponse
import sokos.ske.krav.domain.ske.responses.ValideringsFeilResponse
import sokos.ske.krav.security.MaskinportenAccessTokenClient
import sokos.ske.krav.service.DatabaseService
import sokos.ske.krav.service.Directories
import sokos.ske.krav.service.ENDRE_KRAV
import sokos.ske.krav.service.FtpService
import sokos.ske.krav.service.NYTT_KRAV
import sokos.ske.krav.service.STOPP_KRAV
import sokos.ske.krav.service.SkeService
import sokos.ske.krav.util.FakeFtpService
import sokos.ske.krav.util.MockHttpClient
import sokos.ske.krav.util.TestContainer
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.LocalDate

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
        val ftpService = fakeFtpService.setupMocks(Directories.INBOUND, listOf("fil1.txt"))

        val skeService = SkeService(skeClient, mockkKravService, ftpService)

        skeService.sendNyeFtpFilerTilSkatt()

        val kravdata = ds.connection.use {
            it.hentAlleKravData()
        }
        kravdata.size shouldBe 103

        kravdata.filter { it.kravtype == STOPP_KRAV }.size shouldBe 2
        kravdata.filter { it.kravtype == ENDRE_KRAV }.size shouldBe 4
        kravdata.filter { it.kravtype == NYTT_KRAV }.size shouldBe 97
        kravdata.filter { it.kravtype == NYTT_KRAV && it.saksnummerSKE == "1234" }.size shouldBe 97
        kravdata.filter { it.kravtype == ENDRE_KRAV && it.saksnummerSKE == "1234" }.size shouldBe 4

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
            it.hentAlleKravData()
        }

        kravdata.filter { it.status == MottaksStatusResponse.MottaksStatus.RESKONTROFOERT.value }.size shouldBe 101

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
                status = '${MottaksStatusResponse.MottaksStatus.VALIDERINGSFEIL.value}'
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

fun mockKravService(ds: HikariDataSource): DatabaseService = mockk<DatabaseService>() {
    every { hentSkeKravident(any<String>()) } answers {
        ds.connection.useAndHandleErrors { con ->
            con.hentSkeKravIdent(firstArg<String>())
        }
    }

    every { lagreNyKobling(any<String>()) } answers {
        ds.connection.useAndHandleErrors { con ->
            con.lagreNyKobling(firstArg<String>())
        }
    }


    every {
        lagreNyttKrav(
            any<String>(),
            any<KravLinje>(),
            any<String>(),
            any<HttpStatusCode>(),
        )
    } answers {
        ds.connection.useAndHandleErrors { con ->
            con.lagreNyttKrav(
                arg<String>(0),
                arg<KravLinje>(1),
                arg<String>(2),
                arg<HttpStatusCode>(3),
            )
        }
    }
    every { hentAlleKravMedValideringsfeil() } answers {
        ds.connection.useAndHandleErrors { con ->
            con.hentAlleKravMedValideringsfeil()
        }
    }
    every { lagreValideringsfeil(any<ValideringsFeilResponse>(), any<String>()) } answers {
        ds.connection.useAndHandleErrors { con ->
            con.lagreValideringsfeil(firstArg<ValideringsFeilResponse>(), secondArg<String>())
        }
    }

    every { hentAlleKravSomIkkeErReskotrofort() } answers {
        ds.connection.useAndHandleErrors { con ->
            con.hentAlleKravSomIkkeErReskotrofort()
        }
    }
    every { oppdaterStatus(any<MottaksStatusResponse>()) } answers {
        ds.connection.useAndHandleErrors { con ->
            con.oppdaterStatus(firstArg<MottaksStatusResponse>())
        }
    }
}
