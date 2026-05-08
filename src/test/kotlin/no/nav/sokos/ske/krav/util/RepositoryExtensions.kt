package no.nav.sokos.ske.krav.util

import kotliquery.queryOf

import no.nav.sokos.ske.krav.domain.FilValideringsfeil
import no.nav.sokos.ske.krav.domain.Krav
import no.nav.sokos.ske.krav.repository.FilValideringsfeilRepository
import no.nav.sokos.ske.krav.repository.KravRepository

fun FilValideringsfeilRepository.getAllValideringsFeil(): List<FilValideringsfeil> =
    transaction { session ->
        session.list(
            queryOf("select * from filvalideringsfeil"),
            mapToFilValideringsfeil,
        )
    }

fun FilValideringsfeilRepository.getFilValideringsFeilForFil(filnavn: String): List<FilValideringsfeil> =
    transaction { session ->
        session.list(
            queryOf(
                "select * from filvalideringsfeil where filnavn = ?",
                filnavn,
            ),
            extractor = mapToFilValideringsfeil,
        )
    }

fun KravRepository.getAllKrav(): List<Krav> =
    transaction { session ->
        session.list(
            queryOf("select * from krav"),
            extractor = mapToKrav,
        )
    }
