package no.nav.sokos.ske.krav.service

import no.nav.sokos.ske.krav.database.models.KravTable
import no.nav.sokos.ske.krav.domain.Status
import no.nav.sokos.ske.krav.domain.StonadsType
import no.nav.sokos.ske.krav.domain.StonadsType.Companion.getStonadstype

@RequiresOptIn(message = "Skal bare brukes i frontend")
@Retention(AnnotationRetention.BINARY)
annotation class Frontend

@Frontend
enum class RapportType { AVSTEMMING, RESENDING }

@Frontend
class RapportService(
    private val dbService: DatabaseService = DatabaseService(),
) {
    val kravSomSkalAvstemmes by lazy { mapToRapportObjekt(dbService.getAllKravForAvstemming()) }
    val kravSomSkalResendes by lazy { mapToRapportObjekt(dbService.getAllKravForResending()) }

    fun oppdaterStatusTilRapportert(kravId: Int) = dbService.updateStatusForAvstemtKravToReported(kravId)

    private fun mapToRapportObjekt(liste: List<KravTable>) =
        liste
            .map {
                RapportObjekt(
                    it.kravId.toString(),
                    it.filnavn,
                    it.linjenummer.toString(),
                    it.saksnummerNAV,
                    it.vedtaksDato.toString(),
                    it.fagsystemId,
                    it.kravkode,
                    it.kodeHjemmel,
                    it.status,
                    getStonadstype(it.kravkode, it.kodeHjemmel),
                    it.saksnummerNAV,
                    it.referansenummerGammelSak,
                    it.belop,
                    it.periodeFOM,
                    it.periodeTOM,
                    getFeilmeldinger(it),
                    with(it.tidspunktSisteStatus) {
                        "$dayOfMonth/$monthValue/$year, $hour:$minute"
                    },
                )
            }.distinctBy { it.kravID }

    private fun getFeilmeldinger(krav: KravTable): List<String> =
        if (krav.status != Status.VALIDERINGSFEIL_AV_LINJE_I_FIL.value) {
            dbService.getFeilmeldingForKravId(krav.kravId).map { it.melding.splitToSequence(", mottatt").first() }
        } else {
            emptyList()
        }

    data class RapportObjekt(
        val kravID: String,
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
        val referansenummberGammelSak: String,
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
                    "Saksnummer NAV",
                    "Referansenummer gammel sak",
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
                                    it.kravID.escapeCsvField(),
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
                                    it.referansenummberGammelSak.escapeCsvField(),
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
