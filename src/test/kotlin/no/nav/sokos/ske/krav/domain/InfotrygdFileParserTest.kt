package no.nav.sokos.ske.krav.domain

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

import no.nav.sokos.ske.krav.copybook.FileParser
import no.nav.sokos.ske.krav.util.FtpTestUtil.getFileContent

internal class InfotrygdFileParserTest :
    FunSpec({
        test("Infotrygd fil skal ha riktig avsender") {
            val infotrygdFil = getFileContent("innsender/InfotrygdFil.txt")
            val infotrygdParser = FileParser(infotrygdFil)

            infotrygdParser.kontrollLinjeHeader.left.avsender shouldBe Avsender.INFOTRYGD.name
            infotrygdParser.kontrollLinjeFooter.left.avsender shouldBe Avsender.INFOTRYGD.name

            val kravLinjer = infotrygdParser.kravLinjer()
            kravLinjer.forEach { kravLinje ->
                kravLinje.avsender shouldBe Avsender.INFOTRYGD.name
            }
        }
    })
