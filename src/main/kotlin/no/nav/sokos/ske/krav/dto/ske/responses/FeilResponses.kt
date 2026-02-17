package no.nav.sokos.ske.krav.dto.ske.responses

import kotlinx.serialization.Serializable

@Serializable
data class ValideringsFeilResponse(
    val valideringsfeil: List<ValideringsFeil>,
)

@Serializable
data class ValideringsFeil(
    val error: String,
    val message: String,
)

@Serializable
data class FeilResponse(
    val type: String,
    val title: String,
    val status: Int,
    val detail: String,
    val instance: String,
) {
    object CustomTitles {
        const val FANT_IKKE_GYLDIG_KRAVIDENT = "Fant ikke gyldig kravidentifikator for migrert krav"
    }

    object CustomTypes {
        const val FEIL_FRA_SERVER = "FEIL_FRA_SERVER"
    }
}
