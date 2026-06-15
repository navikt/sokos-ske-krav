package no.nav.sokos.ske.krav.domain

import no.nav.sokos.ske.krav.domain.TaggablePeople.LENE
import no.nav.sokos.ske.krav.domain.TaggablePeople.LINE_ANITA
import no.nav.sokos.ske.krav.domain.TaggablePeople.MARITA
import no.nav.sokos.ske.krav.domain.TaggablePeople.STEINAR
import no.nav.sokos.ske.krav.domain.TaggablePeople.TRINE
import no.nav.sokos.ske.krav.dto.ske.responses.FeilResponse
import no.nav.sokos.ske.krav.validation.LineValidationRules.ErrorKeys.REFERANSENUMMERGAMMELSAK_MISSING

enum class TaggablePeople(
    val slackId: String,
) {
    LENE("<@U08S6FA0XSS>"), // Produktleder
    TRINE("<@UDCM6F8V8>"), // Produkteier
    MARITA("<@UCG179DPT>"), // Fagressurs
    LINE_ANITA("<@U02AVNPT3T9>"), // Fagressurs
    STEINAR("<@U796MGBA9>"), // Teknisk domenespesialist
}

enum class SlackTags(
    val personer: List<TaggablePeople>,
    val rutineLink: String? = null,
    val errorKey: String? = null,
) {
    // Asynkrone valideringsregler https://skatteetaten.github.io/api-dokumentasjon/api/innkrevingsoppdrag?tab=Feilkoder
    PERSON_EKSISTERER_IKKE(listOf(LENE, TRINE)),
    PERSON_ER_DOED(listOf(LENE, TRINE)),
    ORGANISASJONSNUMMER_FINNES_IKKE(listOf(LENE, TRINE)),
    ORGANISASJON_ER_OPPHOERT(
        listOf(MARITA, LINE_ANITA, STEINAR),
        "https://confluence.adeo.no/spaces/TOB/pages/791026050/Rutine+for+manuell+h%C3%A5ndtering+av+innkrevingskrav+til+skatteetaten+SKE",
    ),
    PERSON_ER_SLETTET(listOf(LENE, TRINE)),
    ORGANISASJON_ER_SLETTET(listOf(LENE, TRINE)),

    // Custom
    FANT_IKKE_GYLDIG_KRAVIDENTIFIKATOR(
        listOf(LENE),
        errorKey = FeilResponse.CustomTitles.FANT_IKKE_GYLDIG_KRAVIDENTIFIKATOR,
    ),
    REFERANSENUMMERGAMMELSAK_MANGLER(
        personer = listOf(LENE),
        errorKey = REFERANSENUMMERGAMMELSAK_MISSING,
    ),
    ;

    companion object {
        val lookupMap: Map<String, SlackTags> = entries.associateBy { it.errorKey ?: it.name }
    }
}
