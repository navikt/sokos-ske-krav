package no.nav.sokos.ske.krav.repository

import java.sql.ResultSet

import no.nav.sokos.ske.krav.domain.Feilmelding
import no.nav.sokos.ske.krav.domain.FilValideringsfeil
import no.nav.sokos.ske.krav.domain.Krav
import no.nav.sokos.ske.krav.repository.RepositoryExtensions.getColumn

fun ResultSet.toKrav() =
    toList {
        Krav(
            kravId = getColumn("id"),
            filnavn = getColumn("filnavn"),
            linjenummer = getColumn<Int>("linjenummer"),
            saksnummerNAV = getColumn("saksnummer_nav"),
            kravidentifikatorSKE = getColumn("kravidentifikator_ske"),
            belop = getColumn("belop"),
            vedtaksDato = getColumn("vedtaksDato"),
            gjelderId = getColumn("gjelder_id"),
            periodeFOM = getColumn("periode_fom"),
            periodeTOM = getColumn("periode_tom"),
            kravkode = getColumn("kravkode"),
            referansenummerGammelSak = getColumn("referansenummerGammelSak"),
            transaksjonsDato = getColumn("transaksjonsDato"),
            enhetBosted = getColumn("enhet_bosted"),
            enhetBehandlende = getColumn("enhet_behandlende"),
            kodeHjemmel = getColumn("kode_hjemmel"),
            kodeArsak = getColumn("kode_arsak"),
            belopRente = getColumn("belop_rente"),
            fremtidigYtelse = getColumn("fremtidig_ytelse"),
            utbetalDato = getColumn("utbetaldato"),
            fagsystemId = getColumn("fagsystem_id"),
            status = getColumn("status"),
            kravtype = getColumn("kravtype"),
            corrId = getColumn("corr_id"),
            tidspunktSendt = getColumn("tidspunkt_sendt"),
            tidspunktSisteStatus = getColumn("tidspunkt_siste_status"),
            tidspunktOpprettet = getColumn("tidspunkt_opprettet"),
        )
    }

fun ResultSet.toFeilmelding() =
    toList {
        Feilmelding(
            feilmeldingId = getColumn("id"),
            kravId = getColumn("krav_id"),
            corrId = getColumn("corr_id"),
            saksnummerNav = getColumn("saksnummer_nav"),
            kravidentifikatorSKE = getColumn("kravidentifikator_ske"),
            error = getColumn("error"),
            melding = getColumn("melding"),
            navRequest = getColumn("nav_request"),
            skeResponse = getColumn("ske_response"),
            tidspunktOpprettet = getColumn("tidspunkt_opprettet"),
            rapporter = getColumn("rapporter"),
        )
    }

fun ResultSet.toValideringsfeil() =
    toList {
        FilValideringsfeil(
            valideringsfeilId = getColumn("id"),
            filnavn = getColumn("filnavn"),
            linjenummer = getColumn("linjenummer"),
            saksnummerNav = getColumn("saksnummer_nav"),
            kravLinje = getColumn("kravlinje"),
            feilmelding = getColumn("feilmelding"),
            tidspunktOpprettet = getColumn("tidspunkt_opprettet"),
            rapporter = getColumn("rapporter"),
        )
    }

private fun <T> ResultSet.toList(mapper: ResultSet.() -> T) =
    buildList {
        while (next()) {
            add(mapper())
        }
    }
