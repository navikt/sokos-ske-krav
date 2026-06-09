package no.nav.sokos.ske.krav.validation

import no.nav.sokos.ske.krav.copybook.FileParser
import no.nav.sokos.ske.krav.copybook.KontrollLinjeFooter
import no.nav.sokos.ske.krav.copybook.KontrollLinjeHeader
import no.nav.sokos.ske.krav.copybook.KravLinje
import no.nav.sokos.ske.krav.copybook.ParseResult
import no.nav.sokos.ske.krav.domain.Avsender

class FileValidator {
    object ErrorKeys {
        const val PARSE_EXCEPTION = "Exception i parsing av fil"
        const val FEIL_I_ANTALL = "Antall krav stemmer ikke med antallet i siste linje"
        const val FEIL_I_SUM = "Sum alle linjer stemmer ikke med sum i siste linje"
        const val FEIL_I_DATO = "Dato sendt er avvikende mellom første og siste linje fra OS"
        const val FAGSYSTEMID_MANGLER = "fagsystemId mangler i en eller flere kravlinjer"
    }

    fun validateFile(content: List<String>): ValidationResult =
        when (val parseResult = FileParser(content).parseResult) {
            is ParseResult.Success -> {
                val validationErrors = validateLines(parseResult.kontrollLinjeFooter, parseResult.kontrollLinjeHeader, parseResult.kravLinjer)
                if (validationErrors.isNotEmpty()) {
                    ValidationResult.Error(messages = validationErrors)
                } else {
                    ValidationResult.Success(parseResult.kravLinjer)
                }
            }

            is ParseResult.Error -> {
                val messages = parseResult.messages.map { ErrorKeys.PARSE_EXCEPTION to it }
                ValidationResult.Error(messages = messages)
            }
        }

    private fun validateLines(
        lastLine: KontrollLinjeFooter,
        firstLine: KontrollLinjeHeader,
        kravLinjer: List<KravLinje>,
    ): List<Pair<String, String>> =
        buildList {
            if (lastLine.antallTransaksjoner != kravLinjer.size) {
                add(ErrorKeys.FEIL_I_ANTALL to "Antall krav: ${kravLinjer.size}, Antall i siste linje: ${lastLine.antallTransaksjoner}\n")
            }
            if (kravLinjer.sumOf { it.belop + it.belopRente }.compareTo(lastLine.sumAlleTransaksjoner) != 0) {
                add(ErrorKeys.FEIL_I_SUM to "Sum alle linjer: ${kravLinjer.sumOf { it.belop + it.belopRente }}, Sum siste linje: ${lastLine.sumAlleTransaksjoner}\n")
            }
            if (firstLine.transaksjonsDato != lastLine.transaksjonTimestamp) {
                add(ErrorKeys.FEIL_I_DATO to "Dato første linje: ${firstLine.transaksjonsDato}, Dato siste linje: ${lastLine.transaksjonTimestamp}\n")
            }
            if (kravLinjer.any { it.avsender.trim() == Avsender.OB04.name && it.fagsystemId.isBlank() }) {
                add(ErrorKeys.FAGSYSTEMID_MANGLER to "fagsystemId mangler i en eller flere kravlinjer\n")
            }
        }
}
