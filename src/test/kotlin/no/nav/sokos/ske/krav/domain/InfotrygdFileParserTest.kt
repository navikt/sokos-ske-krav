package no.nav.sokos.ske.krav.domain

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

import no.nav.sokos.ske.krav.copybook.FileParser
import no.nav.sokos.ske.krav.util.FtpTestUtil.getFileContent

internal class InfotrygdFileParserTest :
    FunSpec({
        test("Infotrygd fil skal ha riktig avsender") {
            val infotrygdFil = getFileContent("InfotrygdFil.txt")
            val infotrygdParser = FileParser(infotrygdFil)

            infotrygdParser.parseKontrollLinjeHeader().avsender shouldBe Avsender.INFOTRYGD
            infotrygdParser.parseKontrollLinjeFooter().avsender shouldBe Avsender.INFOTRYGD

            val kravLinjer = infotrygdParser.parseKravLinjer()
            kravLinjer.forEach { kravLinje ->
                kravLinje.avsender shouldBe Avsender.INFOTRYGD
            }
        }
    })
