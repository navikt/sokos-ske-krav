package no.nav.sokos.ske.krav.dto.slack

import no.nav.sokos.ske.krav.dto.ske.responses.FeilResponse
import no.nav.sokos.ske.krav.dto.slack.ExtraTags.Companion.FANT_IKKE_GYLDIG_KRAVIDENTIFIKATOR
import no.nav.sokos.ske.krav.dto.slack.ExtraTags.Companion.ORGANISASJONSNUMMER_FINNES_IKKE
import no.nav.sokos.ske.krav.dto.slack.ExtraTags.Companion.ORGANISASJON_ER_OPPHOERT
import no.nav.sokos.ske.krav.dto.slack.ExtraTags.Companion.ORGANISASJON_ER_SLETTET
import no.nav.sokos.ske.krav.dto.slack.ExtraTags.Companion.PERSON_EKSISTERER_IKKE
import no.nav.sokos.ske.krav.dto.slack.ExtraTags.Companion.PERSON_ER_DOED
import no.nav.sokos.ske.krav.dto.slack.ExtraTags.Companion.PERSON_ER_SLETTET
import no.nav.sokos.ske.krav.dto.slack.ExtraTags.Companion.REFERANSENUMMERGAMMELSAK_MANGLER
import no.nav.sokos.ske.krav.dto.slack.ExtraTags.Companion.ROUTINE_LINK_ORGANISASJON_ER_OPPHOERT
import no.nav.sokos.ske.krav.validation.ErrorCategory
import no.nav.sokos.ske.krav.validation.ErrorKeys

enum class TaggablePeople(
    val slackId: String,
) {
    LENE("<@U08S6FA0XSS>"), // Produktleder
    TRINE("<@UDCM6F8V8>"), // Produkteier
    MARITA("<@UCG179DPT>"), // Fagressurs
    LINE_ANITA("<@U02AVNPT3T9>"), // Fagressurs
    STEINAR("<@U796MGBA9>"), // Teknisk domenespesialist
}

data class FileError(
    val alertTitle: ErrorCategory,
    val filename: String,
    val errorDetails: MutableList<ErrorDetails> = mutableListOf(),
) {
    val extraTags: ExtraTags = ExtraTags()

    init {
        updateExtraTags()
    }

    fun updateExtraTags(errors: List<ErrorDetails> = errorDetails) {
        errors.forEach { error ->
            when {
                error.isError(ORGANISASJON_ER_OPPHOERT) -> {
                    extraTags.rutineLink.add(ROUTINE_LINK_ORGANISASJON_ER_OPPHOERT)
                    extraTags.people.addAll(
                        setOf(
                            TaggablePeople.MARITA,
                            TaggablePeople.LINE_ANITA,
                            TaggablePeople.STEINAR,
                        ),
                    )
                }
                error.isError(PERSON_EKSISTERER_IKKE) ||
                    error.isError(PERSON_ER_DOED) ||
                    error.isError(PERSON_ER_SLETTET) ||
                    error.isError(ORGANISASJONSNUMMER_FINNES_IKKE) ||
                    error.isError(ORGANISASJON_ER_SLETTET) ||
                    error.isError(FANT_IKKE_GYLDIG_KRAVIDENTIFIKATOR) ||
                    error.isError(REFERANSENUMMERGAMMELSAK_MANGLER) -> {
                    extraTags.people.add(TaggablePeople.LENE)
                }
                else -> {
                    extraTags.people.add(TaggablePeople.LENE)
                }
            }
        }
    }

    private fun ErrorDetails.isError(errorType: String) = header.contains(errorType)
}

data class ErrorDetails(
    val header: String,
    val description: String,
    val caseNumber: String? = null,
) {
    constructor(header: ErrorKeys, description: String, caseNumber: String? = null) : this(header.value, description, caseNumber)
}

data class ExtraTags(
    val people: MutableSet<TaggablePeople> = mutableSetOf(),
    val rutineLink: MutableSet<String> = mutableSetOf(),
) {
    companion object {
        // Asynkrone valideringsregler https://skatteetaten.github.io/api-dokumentasjon/api/innkrevingsoppdrag?tab=Feilkoder
        const val PERSON_EKSISTERER_IKKE = "PERSON_EKSISTERER_IKKE"
        const val PERSON_ER_DOED = "PERSON_ER_DOED"
        const val PERSON_ER_SLETTET = "PERSON_ER_SLETTET"
        const val ORGANISASJONSNUMMER_FINNES_IKKE = "ORGANISASJONSNUMMER_FINNES_IKKE"
        const val ORGANISASJON_ER_SLETTET = "ORGANISASJON_ER_SLETTET"
        const val ORGANISASJON_ER_OPPHOERT = "ORGANISASJON_ER_OPPHOERT"

        const val ROUTINE_LINK_ORGANISASJON_ER_OPPHOERT = "https://confluence.adeo.no/spaces/TOB/pages/791026050/Rutine+for+manuell+h%C3%A5ndtering+av+innkrevingskrav+til+skatteetaten+SKE"

        // Custom
        const val FANT_IKKE_GYLDIG_KRAVIDENTIFIKATOR = FeilResponse.CustomTitles.FANT_IKKE_GYLDIG_KRAVIDENTIFIKATOR
        val REFERANSENUMMERGAMMELSAK_MANGLER = ErrorKeys.REFERANSENUMMERGAMMELSAK_MISSING.value
    }
}
