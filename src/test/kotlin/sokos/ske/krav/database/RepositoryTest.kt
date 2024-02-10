package sokos.ske.krav.database

import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.testcontainers.toDataSource
import io.kotest.matchers.shouldBe
import sokos.ske.krav.database.Repository.getAlleKoblinger
import sokos.ske.krav.database.Repository.getAlleKrav
import sokos.ske.krav.database.Repository.koblesakRef
import sokos.ske.krav.database.Repository.insertNewKobling
import sokos.ske.krav.database.Repository.insertNewKrav
import sokos.ske.krav.service.KRAV_SENDT
import sokos.ske.krav.service.NYTT_KRAV
import sokos.ske.krav.util.FileParser
import sokos.ske.krav.util.TestContainer
import sokos.ske.krav.util.isEndring
import sokos.ske.krav.util.isNyttKrav

internal class RepositoryTest : FunSpec({

    val testContainer = TestContainer("RepositoryTest")
    val container = testContainer.getContainer(listOf("NyeKrav.sql"), reusable = false, loadFlyway = true)

    val datasource = container.toDataSource {
        maximumPoolSize = 8
        minimumIdle = 4
        isAutoCommit = false
    }
    afterSpec {
        TestContainer().stopAnyRunningContainer()
    }

    test("Test hent kravdata") {

        val kravData = datasource.connection.use { con ->
            con.getAlleKrav()
        }

        kravData.size shouldBe 2
        kravData.forEachIndexed { i, krav ->
            val index = i + 1
            krav.saksnummerNAV shouldBe "${index}${index}${index}0-navsaksnummer"
            krav.saksnummerSKE shouldBe "$index$index$index$index-skeUUID"
            krav.datoSendt.toString() shouldBe "2023-0$index-01T12:00"
            krav.datoSisteStatus.toString() shouldBe "2023-0$index-01T13:00"
        }

    }

    test("Tester kobling") {

        val kravData = datasource.connection.use { con ->
            con.getAlleKrav()
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

    test("lagring og kobling til endring") {

        val fl1 =
            "00300000035OB040000592759    0000008880020230526148201488362023030120230331FA FØ                     2023052680208020T ANNET                0000000000000000000000"
        val fl2 =
            "00300000035OB040000592759    0000009990020230526148201488362023030120230331FA FØ   OB040000592759    2023052680208020T ANNET                0000000000000000000000"
        val krav1 = FileParser(listOf("", fl1, "")).parseKravLinjer().first()
        val krav2 = FileParser(listOf("", fl2, "")).parseKravLinjer().first()

        krav1.isNyttKrav() shouldBe true
        krav2.isEndring() shouldBe true

        val kobling1 = datasource.connection.use { con ->
            con.insertNewKobling(krav1.saksNummer)
        }

        val krav1NyttSaksNummer = krav1.copy(saksNummer = kobling1)

        println(krav1NyttSaksNummer.toString())
        datasource.connection.use { con ->
            con.insertNewKrav(
                "skeID-001",
                krav1NyttSaksNummer,
                NYTT_KRAV,
                KRAV_SENDT
            )
        }
        val hentetKobling = datasource.connection.use { con ->
            con.koblesakRef(krav2.saksNummer)
        }

        println("kobling 1: $kobling1, hentet kobling: $hentetKobling")

        hentetKobling shouldBe kobling1

    }
})

