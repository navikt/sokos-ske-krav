package sokos.ske.krav.database

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import sokos.ske.krav.database.Repository.getAllKrav
import sokos.ske.krav.database.Repository.getAlleKoblinger
import sokos.ske.krav.util.startContainer

internal class RepositoryTest : FunSpec({

    val datasource = startContainer("RepositoryTest", listOf("NyeKrav.sql"))
    test("Test hent kravdata") {

        val kravData = datasource.connection.use { con ->
            con.getAllKrav()
        }

        kravData.size shouldBe 2
        kravData.forEachIndexed { i, krav ->
            val index = i + 1
            krav.saksnummerNAV shouldBe "${index}${index}${index}0-navsaksnummer"
            krav.saksnummerSKE shouldBe "$index$index$index$index-skeUUID"
            krav.tidspunktSendt.toString() shouldBe "2023-0$index-01T12:00"
            krav.tidspunktSisteStatus.toString() shouldBe "2023-0$index-01T13:00"
        }

    }

    test("Tester kobling") {

        val kravData = datasource.connection.use { con ->
            con.getAllKrav()
        }
        val koblinger = datasource.connection.use { con ->
            con.getAlleKoblinger()
        }
        kravData.size shouldBe 2
        koblinger.size shouldBe 2

        koblinger.forEachIndexed { i, kobling ->
            val index = i + 1
            kobling.saksrefUUID shouldBe "$index$index${index}$index-navuuid"
            kobling.saksrefFraFil shouldBe "$index$index${index}0-navsaksnummer"
        }

    }

})

