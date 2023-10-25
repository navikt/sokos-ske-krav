package sokos.ske.krav

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import sokos.ske.krav.client.SkeClient
import sokos.ske.krav.database.Repository.hentAlleKravData
import sokos.ske.krav.domain.ske.responses.MottaksStatusResponse
import sokos.ske.krav.security.MaskinportenAccessTokenClient
import sokos.ske.krav.service.*
import sokos.ske.krav.util.FakeFtpService
import sokos.ske.krav.util.MockHttpClient
import sokos.ske.krav.util.TestContainer
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.LocalDate


internal class IntegrationTest : FunSpec({
	val tokenProvider = mockk<MaskinportenAccessTokenClient>(relaxed = true)
	val testContainer = TestContainer("IntegrationTest-TestSendNyeKrav")
	val datasource = testContainer.getDataSource(reusable = true, loadFlyway = true)


	afterSpec {
		TestContainer().stopAnyRunningContainer()
		datasource.close()
	}

	test("Kravdata skal lagres i database etter å ha sendt nye krav til SKE") {
		val client = MockHttpClient(kravident = "1234").getClient()

		val fakeFtpService = FakeFtpService()
		val ftpService = fakeFtpService.setupMocks(Directories.INBOUND, listOf("fil1.txt"))

		val mockClient = SkeClient(skeEndpoint = "", client = client, tokenProvider = tokenProvider)
		val service = SkeService(mockClient, datasource, ftpService)

		service.sendNyeFtpFilerTilSkatt()

		val kravdata = datasource.connection.hentAlleKravData()
		kravdata.size shouldBe 101

		kravdata.filter { it.kravtype == STOPP_KRAV }.size shouldBe 2
		kravdata.filter { it.kravtype == ENDRE_KRAV }.size shouldBe 0
		kravdata.filter { it.kravtype == NYTT_KRAV }.size shouldBe 99
		kravdata.filter { it.kravtype == NYTT_KRAV && it.saksnummerSKE == "1234" }.size shouldBe 99

		client.close()
		fakeFtpService.close()

	}


	test("Mottaksstatus skal oppdateres i database") {
		val client = MockHttpClient(kravident = "1234").getClient()

		//   val datasource = TestContainer().getRunningContainer()
		val mockClient = SkeClient(skeEndpoint = "", client = client, tokenProvider = tokenProvider)
		val service = SkeService(mockClient, datasource, mockk<FtpService>())

		service.hentOgOppdaterMottaksStatus()

		val kravdata = datasource.connection.hentAlleKravData()

		kravdata.filter { it.status == MottaksStatusResponse.MottaksStatus.RESKONTROFOERT.value }.size shouldBe 99

		client.close()

	}

	data class ValideringFraDB(val kravidentifikatorSKE: String, val error: String, val melding: String, val dato: Timestamp)

	test("Test hent valideringsfeil") {
		val iderForValideringsFeil = listOf("23", "54", "87")
		val client = MockHttpClient(kravident = "1234", iderForValideringsFeil = iderForValideringsFeil).getClient()

		//    val datasource = TestContainer().getRunningContainer()
		val mockClient = SkeClient(skeEndpoint = "", client = client, tokenProvider = tokenProvider)
		val service = SkeService(mockClient, datasource, mockk<FtpService>())


		datasource.connection.use { con ->
			iderForValideringsFeil.forEach { id ->
				con.prepareStatement(
					"""
                update krav
                set 
                kravidentifikator_ske = $id,
                status = '${MottaksStatusResponse.MottaksStatus.VALIDERINGSFEIL.value}'
                where id = $id;
            """.trimIndent()
				).executeUpdate()
				con.commit()
			}
		}



		service.hentValideringsfeil().size shouldBe 3
		val valideringsFeil = mutableListOf<ValideringFraDB>()
		datasource.connection.use { con ->
			val rs: ResultSet = con.prepareStatement(
				"""select * from validering where kravidentifikator_ske in('23', '54', '87')""".trimIndent()
			).executeQuery()
			while (rs.next()) {
				valideringsFeil.add(
					ValideringFraDB(
						rs.getString("kravidentifikator_ske"),
						rs.getString("error"),
						rs.getString("melding"),
						rs.getTimestamp("dato")
					)
				)
			}
		}
		valideringsFeil.size shouldBe 3

		valideringsFeil.forEach {
			it.error shouldBe "feil"
			it.melding shouldBe "melding"
			it.dato.toString() shouldBe "${LocalDate.now()} 00:00:00.0"
		}
		client.close()

	}
})

