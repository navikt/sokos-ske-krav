package sokos.ske.krav.database

import io.kotest.core.annotation.Ignored
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

import sokos.ske.krav.database.Repository.hentKravData
import sokos.ske.krav.util.DatabaseTestUtils

@Ignored
internal class RepositoryTest: FunSpec( {
    val datasource = DatabaseTestUtils.getDataSource("initDB.sql", false)

    test("Test hent kravdata") {
        val kravData = datasource.connection.hentKravData()

        kravData.size shouldBe 2
        kravData[0].kravidentifikator shouldBe "123-abc"
        kravData[1].kravidentifikator shouldBe "456-def"
    }
})