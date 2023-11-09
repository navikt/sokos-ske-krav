package sokos.ske.krav.util

import sokos.ske.krav.domain.nav.KravLinje
import sokos.ske.krav.domain.ske.requests.TilleggsinformasjonNav

sealed class ValidationResult {
	data class Success(val kravLinjer: List<KravLinje>) : ValidationResult()
	data class Error(val message: List<String>) : ValidationResult()
}

object FileValidator {
	fun validateFiles(content: List<String>): ValidationResult {
		val firstLine = parseFRtoDataFirsLineClass(content.first())
		val lastLine = parseFRtoDataLastLineClass(content.last())
		val detailLines = content.subList(1, content.lastIndex).map { parseFRtoDataDetailLineClass(it) }
		val linesWithInvalidKravKode =
			detailLines.filter { TilleggsinformasjonNav.StoenadsType.from(it.stonadsKode) == null }
		val invalidKravkode = linesWithInvalidKravKode.isNotEmpty()
		val invalidNumberOfLines = lastLine.numTransactionLines != detailLines.size
		val invalidSum = detailLines.sumOf { it.belop + it.belopRente } != lastLine.sumAllTransactionLines
		val invalidTransferDate = firstLine.transferDate != lastLine.transferDate

		if (invalidNumberOfLines || invalidSum || invalidTransferDate || invalidKravkode) {
			val errorMessages = mutableListOf<String>()
			if (invalidNumberOfLines) errorMessages.add("Antall krav stemmer ikke med antallet i siste linje! Antall krav:${detailLines.size}, Antall i siste linje: ${lastLine.numTransactionLines} ")
			if (invalidSum) errorMessages.add("Sum alle linjer stemmer ikke med sum i siste linje! Sum alle linjer: ${detailLines.sumOf { it.belop + it.belopRente }}, Sum siste linje: ${lastLine.sumAllTransactionLines}")
			if (invalidTransferDate) errorMessages.add("Dato sendt er avvikende mellom første og siste linje fra OS! Dato første linje: ${firstLine.transferDate}, Dato siste linje: ${lastLine.transferDate}")
			if (invalidKravkode) errorMessages.add(
				"Ugyldig kravkode! Kravkode: ${
					linesWithInvalidKravKode.map { it.stonadsKode }.toSet()
				}"
			)

			return ValidationResult.Error(errorMessages)
		}
		return ValidationResult.Success(detailLines)
	}
}
