package no.nav.sokos.ske.krav.util

import kotliquery.TransactionalSession
import kotliquery.queryOf

import no.nav.sokos.ske.krav.domain.FilValideringsfeil
import no.nav.sokos.ske.krav.domain.Krav
import no.nav.sokos.ske.krav.repository.FilValideringsfeilRepository
import no.nav.sokos.ske.krav.repository.KravRepository

fun FilValideringsfeilRepository.getAllValideringsFeil(session: TransactionalSession): List<FilValideringsfeil> =
    session.list(
        queryOf("select * from filvalideringsfeil"),
        mapToFilValideringsfeil,
    )

fun FilValideringsfeilRepository.getFilValideringsFeilForFil(
    session: TransactionalSession,
    filnavn: String,
): List<FilValideringsfeil> =
    session.list(
        queryOf(
            "select * from filvalideringsfeil where filnavn = ?",
            filnavn,
        ),
        extractor = mapToFilValideringsfeil,
    )

fun KravRepository.getAllKrav(session: TransactionalSession): List<Krav> =
    session.list(
        queryOf("select * from krav"),
        extractor = mapToKrav,
    )
