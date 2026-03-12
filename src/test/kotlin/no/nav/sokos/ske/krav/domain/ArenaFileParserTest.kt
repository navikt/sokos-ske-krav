package no.nav.sokos.ske.krav.domain

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

import no.nav.sokos.ske.krav.copybook.FileParser
import no.nav.sokos.ske.krav.util.FtpTestUtil.getFileContent

internal class ArenaFileParserTest :
    FunSpec({
        test("Arena fil skal ha riktig avsender og stonadstyper") {
            val arenaFil = getFileContent("innsender/ArenaFil.txt")
            val arenaParser = FileParser(arenaFil)

            arenaParser.parseKontrollLinjeHeader().avsender shouldBe Avsender.ARENA
            arenaParser.parseKontrollLinjeFooter().avsender shouldBe Avsender.ARENA

            val kravLinjer = arenaParser.parseKravLinjer()
            kravLinjer.size shouldBe 18
            kravLinjer.forEach { kravLinje ->
                kravLinje.avsender shouldBe Avsender.ARENA
            }

            val dagpenger = kravLinjer.filter { it.kravKode == "AE DP" }
            dagpenger.size shouldBe 2
            dagpenger.forEach {
                StonadsType.getStonadstype(it.kravKode, it.kodeHjemmel) shouldBe StonadsType.TILBAKEKREVING_DAGPENGER
            }

            val aap = kravLinjer.filter { it.kravKode == "AE AA" }
            aap.size shouldBe 7
            aap.forEach {
                StonadsType.getStonadstype(it.kravKode, it.kodeHjemmel) shouldBe StonadsType.TILBAKEKREVING_ARBEIDSAVKLARINGSPENGER
            }

            val tiltak = kravLinjer.filter { it.kravKode == "AE IS" }
            tiltak.size shouldBe 9
            tiltak.forEach {
                StonadsType.getStonadstype(it.kravKode, it.kodeHjemmel) shouldBe StonadsType.TILBAKEKREVING_TILTAKSPENGER
            }
        }
    })
