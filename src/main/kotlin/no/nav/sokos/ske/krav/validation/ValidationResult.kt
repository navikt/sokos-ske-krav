package no.nav.sokos.ske.krav.validation

import no.nav.sokos.ske.krav.copybook.KravLinje

sealed class ValidationResult {
    data class Success(
        val kravLinjer: List<KravLinje>,
    ) : ValidationResult()

    data class Error(
        val messages: List<Pair<ErrorKeys, String>>,
        val originalLines: List<KravLinje>? = null,
    ) : ValidationResult()
}
