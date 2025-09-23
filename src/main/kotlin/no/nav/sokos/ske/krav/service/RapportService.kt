package no.nav.sokos.ske.krav.service

import com.zaxxer.hikari.HikariDataSource
import kotliquery.sessionOf
import kotliquery.using

import no.nav.sokos.ske.krav.config.DatabaseConfig
import no.nav.sokos.ske.krav.domain.Krav
import no.nav.sokos.ske.krav.domain.Status
import no.nav.sokos.ske.krav.domain.StonadsType
import no.nav.sokos.ske.krav.domain.StonadsType.Companion.getStonadstype
import no.nav.sokos.ske.krav.repository.FeilmeldingRepository
import no.nav.sokos.ske.krav.repository.KravRepository
import no.nav.sokos.ske.krav.util.SQLUtils.transaction

@RequiresOptIn(message = "Skal bare brukes i frontend")
@Retention(AnnotationRetention.BINARY)
annotation class Frontend

@Frontend
enum class RapportType { AVSTEMMING, RESENDING }

@Frontend
class RapportService(
    private val dataSource: HikariDataSource = DatabaseConfig.dataSource,
) {
    val kravSomSkalAvstemmes by lazy { mapToRapportObjekt(using(sessionOf(dataSource)) { KravRepository.getAllKravForAvstemming(it) }) }
    val kravSomSkalResendes by lazy { mapToRapportObjekt(using(sessionOf(dataSource)) { KravRepository.getAllKravForResending(it) }) }

    suspend fun oppdaterStatusTilRapportert(kravId: Int) {
        dataSource.transaction { session ->
            KravRepository.updateStatusForAvstemtKravToReported(session, kravId)
        }
    }

    private fun mapToRapportObjekt(kravListe: List<Krav>) =
        kravListe
            .map { krav ->
                RapportObjekt(
                    krav.kravId.toString(),
                    krav.filnavn,
                    krav.linjenummer.toString(),
                    krav.saksnummerNAV,
                    krav.vedtaksDato.toString(),
                    krav.fagsystemId,
                    krav.kravkode,
                    krav.kodeHjemmel,
                    krav.status,
                    getStonadstype(krav.kravkode, krav.kodeHjemmel),
                    krav.saksnummerNAV,
                    krav.referansenummerGammelSak,
                    krav.belop,
                    krav.periodeFOM,
                    krav.periodeTOM,
                    getFeilmeldinger(krav),
                    with(krav.tidspunktSisteStatus) {
                        "$dayOfMonth/$monthValue/$year, $hour:$minute"
                    },
                )
            }.distinctBy { it.kravId }

    private fun getFeilmeldinger(krav: Krav): List<String> =
        if (krav.status != Status.VALIDERINGSFEIL_AV_LINJE_I_FIL.value) {
            using(sessionOf(dataSource)) { session ->
                FeilmeldingRepository.getFeilmeldingForKravId(session, krav.kravId).map { it.melding.splitToSequence(", mottatt").first() }
            }
        } else {
            emptyList()
        }

    data class RapportObjekt(
        val kravId: String,
        val filnavn: String,
        val linjenummer: String,
        val vedtaksId: String,
        val vedtaksDato: String,
        val fagsystemId: String,
        val kravkode: String,
        val kodeHjemmel: String,
        val status: String,
        val stonadsType: StonadsType,
        val saksnummerNAV: String,
        val referansenummerGammelSak: String,
        val belop: Double,
        val periodeFOM: String,
        val periodeTOM: String,
        val feilmeldinger: List<String>,
        val tidspunktSisteStatus: String,
    ) {
        companion object {
            val csvBuilder = CsvBuilder
            val headers =
                listOf(
                    "Krav-ID",
                    "Filnavn",
                    "Linjenummer",
                    "Vedtaks-ID",
                    "Vedtaksdato",
                    "Fagsystem-ID",
                    "Kravkode",
                    "Hjemmelkode",
                    "Status",
                    "Stønadstype",
                    "Saksnummer Nav",
                    "Ref.nr gammel sak",
                    "Beløp",
                    "Periode Fom",
                    "Periode Tom",
                    "Feilmelding",
                    "Tidspunkt siste status",
                )

            object CsvBuilder {
                fun buildCSV(data: List<RapportObjekt>): String =
                    buildString {
                        appendLine(headers.joinToString(","))
                        data.forEach {
                            val fields =
                                listOf(
                                    it.kravId.escapeCsvField(),
                                    it.filnavn.escapeCsvField(),
                                    it.linjenummer.escapeCsvField(),
                                    it.vedtaksId.escapeCsvField(),
                                    it.vedtaksDato.escapeCsvField(),
                                    it.fagsystemId.escapeCsvField(),
                                    it.kravkode.escapeCsvField(),
                                    it.kodeHjemmel.escapeCsvField(),
                                    it.status.escapeCsvField(),
                                    it.stonadsType.toString().escapeCsvField(),
                                    it.saksnummerNAV.escapeCsvField(),
                                    it.referansenummerGammelSak.escapeCsvField(),
                                    it.belop.toString().escapeCsvField(),
                                    it.periodeFOM.escapeCsvField(),
                                    it.periodeTOM.escapeCsvField(),
                                    it.feilmeldinger.joinToString(";").escapeCsvField(),
                                    it.tidspunktSisteStatus.escapeCsvField(),
                                )
                            appendLine(fields.joinToString(","))
                        }
                    }

                private fun String.escapeCsvField(): String =
                    if (contains(",") || contains("\"") || contains("\n") || contains("\r")) {
                        "\"${replace("\"", "\"\"")}\""
                    } else {
                        this
                    }
            }
        }
    }
}
