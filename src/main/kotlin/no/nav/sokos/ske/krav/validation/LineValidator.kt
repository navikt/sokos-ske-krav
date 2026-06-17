package no.nav.sokos.ske.krav.validation

import no.nav.sokos.ske.krav.copybook.KravLinje
import no.nav.sokos.ske.krav.metrics.Metrics

class LineValidator {
    fun validateNewLines(kravLines: List<KravLinje>): List<ValidationResult> =
        kravLines.map { line ->
            Metrics.numberOfKravRead.increment()
            LineValidationRules.runValidation(line)
        }
    // TODO: Move LineValidatorRule here
}
