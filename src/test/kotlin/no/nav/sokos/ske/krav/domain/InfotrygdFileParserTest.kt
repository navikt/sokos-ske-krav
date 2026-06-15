package no.nav.sokos.ske.krav.domain

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

import no.nav.sokos.ske.krav.copybook.FileParser
import no.nav.sokos.ske.krav.copybook.ParseResult
import no.nav.sokos.ske.krav.util.FtpTestUtil.getFileContent

internal class InfotrygdFileParserTest :
    FunSpec({
        test("Infotrygd fil skal ha riktig avsender") {
            val infotrygdFil = getFileContent("innsender/InfotrygdFil.txt")
            val result = FileParser(infotrygdFil).parseResult.shouldBeInstanceOf<ParseResult.Success>()

            result.kontrollLinjeHeader.avsender shouldBe Avsender.INFOTRYGD.name
            result.kontrollLinjeFooter.avsender shouldBe Avsender.INFOTRYGD.name

            result.kravLinjer.forEach { kravLinje ->
                kravLinje.avsender shouldBe Avsender.INFOTRYGD.name
            }
        }
    })
