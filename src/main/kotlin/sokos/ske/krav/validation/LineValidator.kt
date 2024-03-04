package sokos.ske.krav.validation

import mu.KotlinLogging
import sokos.ske.krav.domain.nav.KravLinje
import sokos.ske.krav.domain.nav.KravtypeMappingFromNAVToSKE
import sokos.ske.krav.metrics.Metrics

private val logger = KotlinLogging.logger{}

object LineValidator{
    fun validateLine(krav: KravLinje, filNavn: String): Boolean{
        return validateKravtype(krav, filNavn)
                && validateBelop(krav, filNavn)
    }


    private fun validateBelop(krav: KravLinje, filNavn: String): Boolean {
        val result = krav.belop.toLong() > 0
        if(!result){
            val message = "Beløp er ikke over null"
            Metrics.lineValidationError.labels(filNavn, krav.linjeNummer.toString(), message).inc()
            logger.info( "Feil i $filNavn på linje ${krav.linjeNummer}: $message")
        }

        return result
    }
    private fun validateKravtype(krav: KravLinje, filNavn: String) =  try {
        KravtypeMappingFromNAVToSKE.getKravtype(krav)
        true
    } catch (e: NotImplementedError){
        Metrics.lineValidationError.labels(filNavn, krav.linjeNummer.toString(), e.message).inc()
        logger.info( "Feil i $filNavn på linje ${krav.linjeNummer}: ${e.message}")
        false
    }
}
