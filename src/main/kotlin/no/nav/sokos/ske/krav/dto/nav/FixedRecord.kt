package no.nav.sokos.ske.krav.dto.nav

import java.math.BigDecimal

data class KontrollLinjeHeader(
    val transaksjonsDato: String,
    val avsender: String,
)

data class KontrollLinjeFooter(
    val transaksjonTimestamp: String,
    val avsender: String,
    val antallTransaksjoner: Int,
    val sumAlleTransaksjoner: BigDecimal,
)
