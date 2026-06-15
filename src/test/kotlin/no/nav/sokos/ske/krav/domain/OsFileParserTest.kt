package no.nav.sokos.ske.krav.domain

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

import no.nav.sokos.ske.krav.copybook.FileParser
import no.nav.sokos.ske.krav.copybook.ParseResult
import no.nav.sokos.ske.krav.util.FtpTestUtil.getFileContent

internal class OsFileParserTest :
    FunSpec({
        val altOkFil = getFileContent("innsender/OppdragFil.txt")
        val result = FileParser(altOkFil).parseResult.shouldBeInstanceOf<ParseResult.Success>()

        test("Sjekk at stønadstyper fra OS mappes riktig") {
            val kravLinjer = result.kravLinjer

            kravLinjer.first { it.kravKode == "PE AP" }.let {
                StonadsType.getStonadstype(it.kravKode, it.kodeHjemmel) shouldBe StonadsType.TILBAKEKREVING_ALDERSPENSJON
            }

            kravLinjer.first { it.kravKode == "EF OG" }.let {
                StonadsType.getStonadstype(it.kravKode, it.kodeHjemmel) shouldBe StonadsType.TILBAKEKREVING_OVERGANGSSTOENAD
            }

            kravLinjer.first { it.kravKode == "BA OR" }.let {
                StonadsType.getStonadstype(it.kravKode, it.kodeHjemmel) shouldBe StonadsType.TILBAKEKREVING_BARNETRYGD
            }

            kravLinjer.first { it.kravKode == "PE UT" && it.kodeHjemmel == "EU" }.let {
                StonadsType.getStonadstype(it.kravKode, it.kodeHjemmel) shouldBe StonadsType.TILBAKEKREVING_UFOERETRYGD_ETTEROPPGJOER
            }
        }
    })
