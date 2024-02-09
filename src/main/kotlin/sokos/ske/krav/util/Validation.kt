package sokos.ske.krav.util

import mu.KotlinLogging
import sokos.ske.krav.domain.nav.KravLinje
import sokos.ske.krav.metrics.Metrics

private val secureLogger = KotlinLogging.logger ("secureLogger" )

sealed class ValidationResult {
    data class Success(val kravLinjer: List<KravLinje>) : ValidationResult()
    data class Error(val messages: List<String>) : ValidationResult()
}
object LineValidator{
   fun validateLine(krav: KravLinje, filNavn: String): Boolean{
       return try {
          KravtypeMappingFromNAVToSKE.getKravtype(krav)
          true
        } catch (e: NotImplementedError){
          Metrics.lineValidationError.labels(filNavn, krav.linjeNummer.toString(), e.message).inc()
          secureLogger.warn( "FEIL I FIL $filNavn PÅ LINJE ${krav.linjeNummer}: ${e.message}")
          false
        }
    }
}
object FileValidator{
    fun validateFile(content: List<String>, fileName: String): ValidationResult {
        val parser = FileParser(content)
        val firstLine = parser.parseKontrollLinjeHeader()
        val lastLine = parser.parseKontrollLinjeFooter()
        val kravLinjer = parser.parseKravLinjer()

        val errorMessages = mutableListOf<String>()

        val invalidNumberOfLines = lastLine.antallTransaksjoner != kravLinjer.size
        val invalidSum = kravLinjer.sumOf { it.belop + it.belopRente } != lastLine.sumAlleTransaksjoner
        val invalidTransferDate = firstLine.transaksjonDato != lastLine.transaksjonDato

        if (invalidNumberOfLines) errorMessages.add("Antall krav stemmer ikke med antallet i siste linje! Antall krav:${kravLinjer.size}, Antall i siste linje: ${lastLine.antallTransaksjoner} ")
        if (invalidSum) errorMessages.add("Sum alle linjer stemmer ikke med sum i siste linje! Sum alle linjer: ${kravLinjer.sumOf { it.belop + it.belopRente }}, Sum siste linje: ${lastLine.sumAlleTransaksjoner}")
        if (invalidTransferDate) errorMessages.add("Dato sendt er avvikende mellom første og siste linje fra OS! Dato første linje: ${firstLine.transaksjonDato}, Dato siste linje: ${lastLine.transaksjonDato}")


        if (errorMessages.isNotEmpty()){
          Metrics.fileValidationError.labels(fileName, errorMessages.toString()).inc()
          secureLogger.info ("Feil i validering av fil $fileName: $errorMessages" )
          return ValidationResult.Error(errorMessages)
        }

        return ValidationResult.Success(kravLinjer)
    }
}
