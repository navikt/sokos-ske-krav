package no.nav.sokos.ske.krav.domain

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

import no.nav.sokos.ske.krav.copybook.FileParser
import no.nav.sokos.ske.krav.util.FtpTestUtil.getFileContent

internal class PesysFileParserTest :
    FunSpec({
        test("Pesys fil skal ha riktig avsender") {
            val pesysFil = getFileContent("PesysFil.txt")
            val pesysParser = FileParser(pesysFil)

            pesysParser.parseKontrollLinjeHeader().avsender shouldBe Avsender.PESYS
            pesysParser.parseKontrollLinjeFooter().avsender shouldBe Avsender.PESYS

            val kravLinjer = pesysParser.parseKravLinjer()
            kravLinjer.forEach { kravLinje ->
                kravLinje.avsender shouldBe Avsender.PESYS
            }

            val gjenlevende = kravLinjer.find { it.kravKode == "PE GP" }
            if (gjenlevende != null) {
                StonadsType.getStonadstype(gjenlevende.kravKode, gjenlevende.kodeHjemmel) shouldBe StonadsType.TILBAKEKREVING_GJENLEVENDE_PENSJON
            }
        }
    })
