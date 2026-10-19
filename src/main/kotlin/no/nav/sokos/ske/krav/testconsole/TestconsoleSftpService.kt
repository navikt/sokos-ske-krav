package no.nav.sokos.ske.krav.testconsole

import java.io.ByteArrayInputStream

import no.nav.sokos.ske.krav.config.SftpConfig
import no.nav.sokos.ske.krav.service.Directories

class TestconsoleSftpService(
    private val sftpConfig: SftpConfig = SftpConfig(),
) {
    fun lastOppFil(
        filnavn: String,
        innhold: ByteArray,
    ) {
        require(filnavn.matches(Regex("[A-Za-z0-9_.-]+\\.txt"))) { "Filnavnet må være et .txt-filnavn uten mappe." }

        sftpConfig.channel { con ->
            ByteArrayInputStream(innhold).use { input ->
                con.put(input, "${Directories.INBOUND.value}/$filnavn")
            }
        }
    }
}
