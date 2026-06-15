package no.nav.sokos.ske.krav.domain

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

import no.nav.sokos.ske.krav.copybook.FileParser
import no.nav.sokos.ske.krav.copybook.ParseResult
import no.nav.sokos.ske.krav.util.FtpTestUtil.getFileContent

internal class PesysFileParserTest :
    FunSpec({
        test("Pesys fil skal ha riktig avsender") {
            val pesysFil = getFileContent("innsender/PesysFil.txt")
            val result = FileParser(pesysFil).parseResult.shouldBeInstanceOf<ParseResult.Success>()

            result.kontrollLinjeHeader.avsender shouldBe Avsender.PESYS.name
            result.kontrollLinjeFooter.avsender shouldBe Avsender.PESYS.name

            val kravLinjer = result.kravLinjer
            kravLinjer.forEach { kravLinje ->
                kravLinje.avsender shouldBe Avsender.PESYS.name
            }

            val gjenlevende = kravLinjer.first { it.kravKode == "PE GP" }

            StonadsType.getStonadstype(gjenlevende.kravKode, gjenlevende.kodeHjemmel) shouldBe StonadsType.TILBAKEKREVING_GJENLEVENDE_PENSJON
        }
    })
