package no.nav.sokos.ske.krav.service

import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import kotlinx.coroutines.delay

import com.zaxxer.hikari.HikariDataSource
import io.ktor.http.isSuccess

import no.nav.sokos.ske.krav.client.SlackService
import no.nav.sokos.ske.krav.config.DatabaseConfig
import no.nav.sokos.ske.krav.domain.ENDRING_HOVEDSTOL
import no.nav.sokos.ske.krav.domain.ENDRING_RENTE
import no.nav.sokos.ske.krav.domain.NYTT_KRAV
import no.nav.sokos.ske.krav.domain.STOPP_KRAV
import no.nav.sokos.ske.krav.dto.nav.FtpFilDTO
import no.nav.sokos.ske.krav.dto.nav.KravLinje
import no.nav.sokos.ske.krav.repository.ValideringsfeilRepository
import no.nav.sokos.ske.krav.util.RequestResult
import no.nav.sokos.ske.krav.util.SQLUtils.transaction
import no.nav.sokos.ske.krav.validation.FileValidator
import no.nav.sokos.ske.krav.validation.ValidationResult

private val logger = mu.KotlinLogging.logger {}

class SkeService(
    private val dataSource: HikariDataSource = DatabaseConfig.dataSource,
    private val kravService: KravService = KravService(dataSource),
    private val valideringsfeilRepository: ValideringsfeilRepository = ValideringsfeilRepository(dataSource),
    private val slackService: SlackService = SlackService(),
    private val lineValidatorService: LineValidatorService = LineValidatorService(dataSource, slackService = slackService),
    private val ftpService: FtpService = FtpService(),
) {
    private var haltRun = false

    suspend fun behandleSkeKrav() {
        if (haltRun) {
            logger.info("*** Kjøring er blokkert ***")
            return
        }

        runCatching {
            kravService.resendKrav()

            behandleNyeKravFraFiler()
            delay(5000)

            kravService.resendKrav()

            slackService.sendErrors()
        }.onFailure { exception ->
            logger.error(exception.message)
        }

        if (haltRun) {
            haltRun = false
            logger.info("*** Kjøring er ublokkert ***")
        }
    }

    suspend fun behandleNyeKravFraFiler() {
        val fileListe = ftpService.downloadFiles()
        if (fileListe.isEmpty()) {
            logger.info("*** Ingen nye filer ***")
            return
        }

        val filtekst = if (fileListe.size == 1) "fil" else "filer"
        val datetime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm"))
        logger.info("*** Starter sending av ${fileListe.size} $filtekst $datetime***")

        fileListe.forEach { (fileName, fileContent) ->
            when (val validationResult = FileValidator.validateFile(fileContent)) {
                is ValidationResult.Success -> {
                    val ftpFilDTO = FtpFilDTO(fileName, fileContent, validationResult.kravLinjer)

                    logger.info("Antall krav i $fileName: ${ftpFilDTO.kravLinjer.size}")

                    val kravLinjeListe = lineValidatorService.validateNewLines(ftpFilDTO)
                    handleValidationResults(ftpFilDTO, kravLinjeListe)

                    kravService.opprettKravFraFilOgOppdatereStatus(kravLinjeListe, fileName)
                    kravService.sendKrav(kravService.getKravListe(IKKE_SENT_KRAV)).also { logResult(it) }
                    ftpService.moveFile(fileName, Directories.INBOUND, Directories.OUTBOUND)
                }

                is ValidationResult.Error -> {
                    logger.warn("*** Feil i validering av fil $fileName ***")

                    dataSource.transaction { session ->
                        validationResult.messages.forEach { (first, second) ->
                            slackService.addError(fileName, "Feil i validering av fil", validationResult.messages)
                            slackService.sendErrors()
                            valideringsfeilRepository.insertFileValideringsfeil(fileName, "$first: $second", session)
                        }
                    }

                    ftpService.moveFile(fileName, Directories.INBOUND, Directories.FAILED)
                }
            }
        }
    }

    private fun handleValidationResults(
        ftpFilDTO: FtpFilDTO,
        kravLinjeListe: List<KravLinje>,
    ) {
        if (ftpFilDTO.kravLinjer.size > kravLinjeListe.size) {
            logger.warn("Ved validering av linjer i fil ${ftpFilDTO.name} har ${ftpFilDTO.kravLinjer.size - kravLinjeListe.size} linjer velideringsfeil ")
        }
        if (kravLinjeListe.size >= 1000) {
            logger.info("***Stor fil. Blokkerer kjøring***")
            haltRun = true
        }
    }

    suspend fun checkKravDateForAlert() {
        kravService
            .getKravListe(KRAV_FOR_STATUS_CHECK)
            .filter { it.tidspunktSendt?.isBefore((LocalDateTime.now().minusHours(24))) == true }
            .also {
                if (it.isNotEmpty()) logger.info { "Krav med saksnummer ${it.joinToString { krav -> krav.saksnummerNAV }} har blitt forsøkt resendt i over én dag" }
            }.forEach {
                slackService.addError(
                    it.filnavn,
                    "Krav har blitt forsøkt resendt for lenge",
                    Pair(
                        "Krav har blitt forsøkt resendt i over 24t",
                        "Krav med saksnummer ${it.saksnummerNAV} har blitt forsøkt resendt i ${Duration.between(it.tidspunktSendt, LocalDateTime.now()).toDays()} dager.\n" +
                            "Kravet har status ${it.status} og ble originalt sendt ${it.tidspunktSendt?.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"))}",
                    ),
                )
            }
        slackService.sendErrors()
    }

    private fun logResult(result: List<RequestResult>) {
        val successful = result.filter { it.response.status.isSuccess() }
        val unsuccessful = result.size - successful.size
        logger.info { "Sendte ${result.size} krav${if (unsuccessful > 0) ". $unsuccessful feilet" else ""}" }

        val nye = successful.count { it.krav.kravtype == NYTT_KRAV }
        val endringer = successful.count { it.krav.kravtype == ENDRING_RENTE } + successful.count { it.krav.kravtype == ENDRING_HOVEDSTOL }
        val stopp = successful.count { it.krav.kravtype == STOPP_KRAV }
        logger.info { "$nye nye, $endringer endringer, $stopp stopp" }
    }
}
