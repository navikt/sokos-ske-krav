package sokos.ske.krav.database

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDateTime

import sokos.ske.krav.database.Repository.hentKravData
import sokos.ske.krav.util.DatabaseTestUtils



internal class RepositoryTest: FunSpec( {


    test("Test hent kravdata") {
        val datasource = DatabaseTestUtils.getDataSource("initDB.sql", false)
        val kravData = datasource.connection.hentKravData()

        kravData.size shouldBe 2
        kravData.forEachIndexed { i, krav ->
            val index = i + 1
            krav.saksnummer_nav shouldBe "$index$index$index$index-nav"
            krav.saksnummer_ske shouldBe "$index$index$index$index-ske"
            krav.fildata_nav shouldBe "fildata fra nav $index"
            krav.jsondata_ske shouldBe "json fra ske $index"
            krav.dato_sendt shouldBe LocalDateTime.parse("2023-0$index-01T00:00:00")
            krav.dato_siste_status shouldBe LocalDateTime.parse("2023-0$index-02T00:00:00")
        }
        datasource.close()
    }

})
