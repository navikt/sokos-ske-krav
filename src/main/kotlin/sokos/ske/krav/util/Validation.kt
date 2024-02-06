package sokos.ske.krav.util

import mu.KotlinLogging
import sokos.ske.krav.domain.nav.KravLinje
import sokos.ske.krav.metrics.Metrics.feilIFilValidering
import sokos.ske.krav.metrics.Metrics.feilILinjeValidering

private val secureLogger = KotlinLogging.logger ("secureLogger" )

sealed class ValidationResult {
    data class Success(val kravLinjer: List<KravLinje>) : ValidationResult()
    data class Error(val messages: List<String>) : ValidationResult()
}
object LineValidator{
   fun validateLine(krav: KravLinje, filNavn: String): Boolean{
       return try {
          KravTypeMappingFraNAVTilSKE.getKravtype(krav)
          true
        } catch (e: NotImplementedError){ 
          feilILinjeValidering(filNavn, krav.linjeNummer.toString(), e.message.toString()).increment()
          secureLogger.warn( "FEIL I FIL $filNavn PÅ LINJE ${krav.linjeNummer}: ${e.message}")
           false
        } catch (e: Exception){
          feilILinjeValidering(filNavn, krav.linjeNummer.toString(), e.message.toString()).increment()
          secureLogger.warn( "FEIL I FIL $filNavn PÅ LINJE ${krav.linjeNummer}: ${e.message}")
           false
        }
    }
}
object FileValidator{
    fun validateFile(content: List<String>, fileName: String): ValidationResult {
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


        if (errorMessages.isNotEmpty()){
          feilIFilValidering(fileName, errorMessages.toString()).increment()
          secureLogger.warn ("Feil i validering av fil $fileName: $errorMessages" )
          return ValidationResult.Error(errorMessages)
        }

        return ValidationResult.Success(kravLinjer)
    }
}
