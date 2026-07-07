package no.nav.sokos.ske.krav.validation

import no.nav.sokos.ske.krav.copybook.KravLinje
import no.nav.sokos.ske.krav.dto.slack.ErrorDetails

sealed class ValidationResult {
    data class Success(
        val kravLinjer: List<KravLinje>,
    ) : ValidationResult()

    data class Error(
        val errors: List<ErrorDetails>,
        val originalLines: List<KravLinje>? = null,
    ) : ValidationResult()
}
