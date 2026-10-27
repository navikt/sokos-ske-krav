package no.nav.sokos.ske.krav.domain

import kotlinx.serialization.Serializable

@Serializable
data class Avstemmingsgrunnlag(
    val kilde: String,
    val trekkgrunnlag: Double,
    val belop: Double,
    val renter: Double,
    val behandlendeEnhet: String,
    val boenhet: String,
    val datafil: String,
    val foedselsnummer: String,
    val aarsakskode: String,
    val lovhjemmel: String,
    val ytelse: String,
    val vedtaksperiodeFraOgMed: String,
    val vedtaksperiodeTilOgMed: String,
    val refGmlVedtak: String,
    val saksnummer: String,
    val transaksjonsdato: String,
    val vedtaksdato: String,
)
