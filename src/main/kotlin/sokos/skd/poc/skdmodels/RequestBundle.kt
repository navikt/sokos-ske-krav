package sokos.skd.poc.skdmodels

import sokos.skd.poc.navmodels.DetailLine

data class RequestBundle(
    val opprettInnkrevingsoppdragRequestListe: List<OpprettInnkrevingsoppdragRequest>,
    val endringRequestListe: List<EndringRequest>,
    val stoppKravRequestListe: List<AvskrivingRequest>,
    val feilLinjerfraFil: List<DetailLine>
) {
}