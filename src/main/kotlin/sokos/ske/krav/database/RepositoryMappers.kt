package sokos.ske.krav.database

import sokos.ske.krav.database.RepositoryExtensions.getColumn
import sokos.ske.krav.database.models.FeilmeldingTable
import sokos.ske.krav.database.models.KravTable
import sokos.ske.krav.database.models.ValideringsfeilTable
import java.sql.ResultSet

fun ResultSet.toKrav() =
    toList {
        KravTable(
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
        FeilmeldingTable(
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
        )
    }

fun ResultSet.toValideringsfeil() =
    toList {
        ValideringsfeilTable(
            valideringsfeilId = getColumn("id"),
            filnavn = getColumn("filnavn"),
            linjenummer = getColumn("linjenummer"),
            saksnummerNav = getColumn("saksnummer_nav"),
            kravLinje = getColumn("kravlinje"),
            feilmelding = getColumn("feilmelding"),
            tidspunktOpprettet = getColumn("tidspunkt_opprettet"),
        )
    }

private fun <T> ResultSet.toList(mapper: ResultSet.() -> T) =
    buildList {
        while (next()) {
            add(mapper())
        }
    }
