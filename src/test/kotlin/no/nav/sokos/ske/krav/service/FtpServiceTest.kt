package no.nav.sokos.ske.krav.service

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe

import no.nav.sokos.ske.krav.config.SftpConfig
import no.nav.sokos.ske.krav.listener.SftpListener
import no.nav.sokos.ske.krav.listener.SftpListener.clearAllDirectories

private const val FILE_A = "Fil-A.txt"
private const val FILE_B = "Fil-B.txt"
private const val FILE_OK = "AltOkFil.txt"
private const val FILE_ERROR = "FilMedFeilAntallKrav.txt"

class FtpServiceTest :
    BehaviorSpec({
        extensions(SftpListener)

        val ftpService: FtpService by lazy {
            FtpService(SftpConfig(SftpListener.sftpProperties))
        }

        Given("listFiles kalles") {
            clearAllDirectories()
            listOf(Directories.INBOUND, Directories.OUTBOUND, Directories.FAILED).forEach { directory ->

                When("Directory er ${directory.name}") {

                    Then("Skal listFiles returnere filer i ${directory.name}") {
                        SftpListener.putFiles(listOf(FILE_A, FILE_B), directory)
                        val filesInDir = ftpService.listFiles(directory)
                        filesInDir.size shouldBe 2
                        filesInDir shouldContain FILE_A
                        filesInDir shouldContain FILE_B
                    }
                }
            }
        }
        Given("moveFile kalles") {
            clearAllDirectories()
            listOf(
                Pair(Directories.INBOUND, Directories.OUTBOUND),
                Pair(Directories.INBOUND, Directories.FAILED),
            ).forEach { (from, to) ->
                When("flytter fil fra ${from.name} til ${to.name}") {

                    Then("Skal filen flyttes fra ${from.name} til ${to.name}") {
                        SftpListener.putFiles(listOf(FILE_A), from)
                        ftpService.moveFile(FILE_A, from, to)
                        val filesInDir = ftpService.listFiles(to)
                        filesInDir.size shouldBe 1
                        filesInDir shouldContain FILE_A
                    }
                }
            }
        }
    })
