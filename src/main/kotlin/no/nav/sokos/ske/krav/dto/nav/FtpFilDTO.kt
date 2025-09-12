package no.nav.sokos.ske.krav.dto.nav

data class FtpFilDTO(
    val name: String,
    val content: List<String>,
    val kravLinjer: List<KravLinje>,
)
