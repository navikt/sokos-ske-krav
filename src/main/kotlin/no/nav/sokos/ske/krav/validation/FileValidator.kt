package no.nav.sokos.ske.krav.validation

import no.nav.sokos.ske.krav.copybook.FileParser
import no.nav.sokos.ske.krav.copybook.KontrollLinjeFooter
import no.nav.sokos.ske.krav.copybook.KontrollLinjeHeader
import no.nav.sokos.ske.krav.copybook.KravLinje
import no.nav.sokos.ske.krav.copybook.ParseResult
import no.nav.sokos.ske.krav.domain.Avsender
import no.nav.sokos.ske.krav.dto.slack.ErrorDetails

class FileValidator {
    fun validateFile(content: List<String>): ValidationResult =
        when (val parseResult = FileParser(content).parseResult) {
            is ParseResult.Success -> {
                val validationErrors = validateLines(parseResult.kontrollLinjeFooter, parseResult.kontrollLinjeHeader, parseResult.kravLinjer)
                if (validationErrors.isNotEmpty()) {
                    ValidationResult.Error(errors = validationErrors)
                } else {
                    ValidationResult.Success(parseResult.kravLinjer)
                }
            }

            is ParseResult.Error -> {
                val messages =
                    parseResult.messages.map { message ->
                        ErrorDetails(
                            header = ErrorKeys.PARSE_EXCEPTION,
                            description = message,
                        )
                    }
                ValidationResult.Error(errors = messages)
            }
        }

    private fun validateLines(
        lastLine: KontrollLinjeFooter,
        firstLine: KontrollLinjeHeader,
        kravLinjer: List<KravLinje>,
    ): List<ErrorDetails> =
        buildList {
            if (lastLine.antallTransaksjoner != kravLinjer.size) {
                add(ErrorDetails(ErrorKeys.FEIL_I_ANTALL, "Antall krav: ${kravLinjer.size}, Antall i siste linje: ${lastLine.antallTransaksjoner}\n"))
            }
            if (kravLinjer.sumOf { it.belop + it.belopRente }.compareTo(lastLine.sumAlleTransaksjoner) != 0) {
                add(ErrorDetails(ErrorKeys.FEIL_I_SUM, "Sum alle linjer: ${kravLinjer.sumOf { it.belop + it.belopRente }}, Sum siste linje: ${lastLine.sumAlleTransaksjoner}\n"))
            }
            if (firstLine.transaksjonsDato != lastLine.transaksjonTimestamp) {
                add(ErrorDetails(ErrorKeys.FEIL_I_DATO, "Dato første linje: ${firstLine.transaksjonsDato}, Dato siste linje: ${lastLine.transaksjonTimestamp}\n"))
            }
            if (kravLinjer.any { it.avsender.trim() == Avsender.OB04.name && it.fagsystemId.isBlank() }) {
                add(ErrorDetails(ErrorKeys.FAGSYSTEMID_MANGLER, "FagsystemId mangler i en eller flere kravlinjer\n"))
            }
        }
}
