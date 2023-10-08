package sokos.ske.krav.database

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.json.Json
import sokos.ske.krav.database.Repository.hentAlleKoblinger
import sokos.ske.krav.database.Repository.hentAlleKravData
import sokos.ske.krav.database.Repository.koblesakRef
import sokos.ske.krav.database.Repository.lagreNyKobling
import sokos.ske.krav.database.Repository.lagreNyttKrav
import sokos.ske.krav.navmodels.DetailLine
import sokos.ske.krav.replaceSaksnrInDetailline
import sokos.ske.krav.service.lagOpprettKravRequest
import sokos.ske.krav.service.parseFRtoDataDetailLineClass
import sokos.ske.krav.skemodels.requests.OpprettInnkrevingsoppdragRequest
import sokos.ske.krav.util.DatabaseTestUtils
import kotlin.math.roundToLong


internal class RepositoryTest: FunSpec( {


    test("Test hent kravdata") {
        val datasource = DatabaseTestUtils.getDataSource("initDB.sql", false)
        val kravData = datasource.connection.hentAlleKravData()

        kravData.size shouldBe 2
        kravData.forEachIndexed { i, krav ->
            val index = i + 1
            krav.saksnummer_nav shouldBe "$index$index$index$index-navuuid"
            krav.saksnummer_ske shouldBe "$index$index$index$index-ske"
            krav.fildata_nav shouldBe "fildata fra nav $index"
            krav.jsondata_ske shouldBe "json fra ske $index"
            krav.dato_sendt shouldBe LocalDateTime.parse("2023-0$index-01T00:00:00")
            krav.dato_siste_status shouldBe LocalDateTime.parse("2023-0$index-02T00:00:00")
        }
        datasource.close()
    }

    test("Tester kobling"){
        val datasource = DatabaseTestUtils.getDataSource("initDB.sql", false)
        val con = datasource.connection
        val kravData = con.hentAlleKravData()
        val koblinger = con.hentAlleKoblinger()
        kravData.size shouldBe 2
        koblinger.size shouldBe 2

        koblinger.forEachIndexed { i, kobling ->
            val index = i + 1
            kobling.saksref_uuid shouldBe "${kravData[i].saksnummer_nav}"
            kobling.saksref_fil shouldBe "$index$index${index}0-navfil"
        }
        datasource.close()
    }

    test("lagring og kobling til endring"){
        val datasource = DatabaseTestUtils.getDataSource("initDB.sql", false)
        val con = datasource.connection
        val fl1 = "00300000035OB040000592759    0000008880020230526148201488362023030120230331FA FØ                     2023052680208020T ANNET                0000000000000000000000"
        val fl2 = "00300000035OB040000592759    0000009990020230526148201488362023030120230331FA FØ   OB040000592759    2023052680208020T ANNET                0000000000000000000000"
        val detail1 = parseFRtoDataDetailLineClass(fl1)
        val detail2 = parseFRtoDataDetailLineClass(fl2)

        detail1.erNyttKrav() shouldBe true
        detail2.erEndring() shouldBe true
        val ekspektedSaksnr = detail1.saksNummer

        val kobling1 = con.lagreNyKobling(detail1.saksNummer)
        val detail1a = replaceSaksnrInDetailline(detail1, kobling1)
        val request1 = lagOpprettKravRequest(detail1a)

        con.lagreNyttKrav("skeID-001", Json.encodeToString(OpprettInnkrevingsoppdragRequest.serializer(),request1), fl1, detail1a, "NYTT_KRAV")

        val hentetKobling =con.koblesakRef(detail2.saksNummer)

        println("kobling 1: $kobling1, hentet kobling: $hentetKobling")

        hentetKobling shouldBe kobling1



        datasource.close()
    }
})

private fun DetailLine.erNyttKrav() = (!this.erEndring() && !this.erStopp())
private fun DetailLine.erEndring() = (referanseNummerGammelSak.isNotEmpty() && !erStopp())
private fun DetailLine.erStopp() = (belop.roundToLong() == 0L)
