package no.nav.sokos.ske.krav.service.unit

import java.time.LocalDate
import java.time.LocalDateTime

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode

import no.nav.sokos.ske.krav.domain.Krav
import no.nav.sokos.ske.krav.domain.Status
import no.nav.sokos.ske.krav.service.ENDRING_HOVEDSTOL
import no.nav.sokos.ske.krav.service.ENDRING_RENTE
import no.nav.sokos.ske.krav.service.NYTT_KRAV
import no.nav.sokos.ske.krav.service.STOPP_KRAV
import no.nav.sokos.ske.krav.service.SkeService.Companion.aggregertPerFil
import no.nav.sokos.ske.krav.util.RequestResult

class RequestResultTest :
    BehaviorSpec({

        Given("Resultater fra forskjellige filer med forskjellige kravtyper") {
            val resultater =
                listOf(
                    kravSendt("fil-en", NYTT_KRAV),
                    kravSendt("fil-to", ENDRING_RENTE),
                    kravSendt("fil-to", ENDRING_RENTE),
                    kravSendt("fil-to", ENDRING_RENTE),
                    kravSendt("fil-tre", STOPP_KRAV),
                    kravSendt("fil-tre", NYTT_KRAV),
                    kravSendt("fil-to", ENDRING_HOVEDSTOL),
                )

            Then("Skal aggregatet telle alle de forskjellige kravene fordelt på filer") {
                val aggregat = resultater.aggregertPerFil()
                aggregat.size shouldBe 3
                aggregat["fil-en"]?.new shouldBe 1
                aggregat["fil-en"]?.changes shouldBe 0
                aggregat["fil-en"]?.stops shouldBe 0

                aggregat["fil-to"]?.new shouldBe 0
                aggregat["fil-to"]?.changes shouldBe 4
                aggregat["fil-to"]?.stops shouldBe 0

                aggregat["fil-tre"]?.new shouldBe 1
                aggregat["fil-tre"]?.changes shouldBe 0
                aggregat["fil-tre"]?.stops shouldBe 1
            }
        }

        Given("Resultater fra én fil med forskjellige kravtyper") {
            val resultater = listOf(kravSendt("fil", NYTT_KRAV), kravSendt("fil", ENDRING_RENTE), kravSendt("fil", STOPP_KRAV), kravSendt("fil", ENDRING_HOVEDSTOL))

            Then("Skal aggregatet telle alle de forskjellige kravene") {
                val aggregat = resultater.aggregertPerFil()
                aggregat.size shouldBe 1
                aggregat.keys.first() shouldBe "fil"
                aggregat["fil"]?.new shouldBe 1
                aggregat["fil"]?.changes shouldBe 2
                aggregat["fil"]?.stops shouldBe 1
            }
        }

        Given("Resultater for bare nye krav fra én fil") {
            val resultater = listOf(kravSendt("fil", NYTT_KRAV), kravSendt("fil", NYTT_KRAV), kravSendt("fil", NYTT_KRAV))

            Then("Skal aggregatet telle tre nye krav og ingen andre krav") {
                val aggregat = resultater.aggregertPerFil()
                aggregat.size shouldBe 1
                aggregat.keys.first() shouldBe "fil"
                aggregat["fil"]?.new shouldBe 3
                aggregat["fil"]?.changes shouldBe 0
                aggregat["fil"]?.stops shouldBe 0
            }
        }
    })

private fun kravSendt(
    filnavn: String,
    kravtype: String,
) = RequestResult("", HttpStatusCode.OK, krav(filnavn, kravtype), "", "", Status.KRAV_SENDT, null)

private fun krav(
    filnavn: String,
    kravtype: String,
) = Krav(
    kravId = 1,
    filnavn = filnavn,
    linjenummer = 1,
    kravidentifikatorSKE = "",
    saksnummerNAV = "",
    belop = 0.0,
    vedtaksDato = LocalDate.now(),
    gjelderId = "",
    periodeFOM = "",
    periodeTOM = "",
    kravkode = "",
    referansenummerGammelSak = "",
    transaksjonsDato = "",
    enhetBosted = "",
    enhetBehandlende = "",
    kodeHjemmel = "",
    kodeArsak = "",
    belopRente = 0.0,
    fremtidigYtelse = 0.0,
    utbetalDato = LocalDate.now(),
    fagsystemId = "",
    status = "",
    kravtype = kravtype,
    corrId = "",
    tidspunktSendt = LocalDateTime.now(),
    tidspunktSisteStatus = LocalDateTime.now(),
    tidspunktOpprettet = LocalDateTime.now(),
    tilleggsfrist = null,
    avsender = "",
)
