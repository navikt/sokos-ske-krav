package sokos.ske.krav.util

import sokos.ske.krav.api.model.requests.TilleggsinformasjonNav
import sokos.ske.krav.domain.DetailLine

sealed class ValidationResult {
	data class Success(val detailLines: List<DetailLine>) : ValidationResult()
	data class Error(val message: List<String>) : ValidationResult()
}

fun fileValidator(content: List<String>): ValidationResult {
	val firstLine = parseFRtoDataFirsLineClass(content.first())
	val lastLine = parseFRtoDataLastLineClass(content.last())
	val detailLines = content.subList(1, content.lastIndex).map { parseFRtoDataDetailLineClass(it) }

	val invalidKravkode = detailLines.any { TilleggsinformasjonNav.StoenadsType.from(it.kravkode) == null }
	val invalidNumberOfLines = lastLine.numTransactionLines != detailLines.size
	val invalidSum = detailLines.sumOf { it.belop + it.belopRente } != lastLine.sumAllTransactionLines
	val invalidTransferDate = firstLine.transferDate != lastLine.transferDate

	if (invalidNumberOfLines || invalidSum || invalidTransferDate || invalidKravkode) {
		val errorMessages = mutableListOf<String>()
		if (invalidNumberOfLines) errorMessages.add("Antall krav stemmer ikke med antallet i siste linje!")
		if (invalidSum) errorMessages.add("Sum alle linjer stemmer ikke med sum i siste linje!")
		if (invalidTransferDate) errorMessages.add("Dato sendt er avvikende mellom første og siste linje fra OS!")
		if (invalidKravkode) errorMessages.add("Ugyldig kravkode!")

		return ValidationResult.Error(errorMessages)
	}
	return ValidationResult.Success(detailLines)
}