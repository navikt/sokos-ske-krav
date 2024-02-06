package sokos.ske.krav.util

import sokos.ske.krav.domain.nav.KravLinje


sealed class ValidationResult {
    data class Success(val kravLinjer: List<KravLinje>) : ValidationResult()
    data class Error(val messages: List<String>) : ValidationResult()
}

object FileValidator {
    fun validateFiles(content: List<String>): ValidationResult {
        val parser = FilParser(content)
        val firstLine = parser.parseForsteLinje()
        val lastLine = parser.parseSisteLinje()
        val kravLinjer = parser.parseKravLinjer()

        val errorMessages = mutableListOf<String>()

        val invalidNumberOfLines = lastLine.numTransactionLines != kravLinjer.size
        val invalidSum = kravLinjer.sumOf { it.belop + it.belopRente } != lastLine.sumAllTransactionLines
        val invalidTransferDate = firstLine.transferDate != lastLine.transferDate

        if (invalidNumberOfLines) errorMessages.add("Antall krav stemmer ikke med antallet i siste linje! Antall krav:${kravLinjer.size}, Antall i siste linje: ${lastLine.numTransactionLines} ")
        if (invalidSum) errorMessages.add("Sum alle linjer stemmer ikke med sum i siste linje! Sum alle linjer: ${kravLinjer.sumOf { it.belop + it.belopRente }}, Sum siste linje: ${lastLine.sumAllTransactionLines}")
        if (invalidTransferDate) errorMessages.add("Dato sendt er avvikende mellom første og siste linje fra OS! Dato første linje: ${firstLine.transferDate}, Dato siste linje: ${lastLine.transferDate}")


        if (errorMessages.isNotEmpty()) return ValidationResult.Error(errorMessages)

        return ValidationResult.Success(kravLinjer)
    }
}
