package sokos.ske.krav.validation

import sokos.ske.krav.domain.nav.KravLinje

sealed class ValidationResult {
    data class Success(
        val kravLinjer: List<KravLinje>,
    ) : ValidationResult()

    data class Error(
        val messages: List<Pair<String, String>>,
    ) : ValidationResult()
}
