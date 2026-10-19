package no.nav.sokos.ske.krav.frontend

import kotlinx.html.div
import kotlinx.html.stream.createHTML

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.string.shouldContain
import io.ktor.server.html.insert

import no.nav.sokos.ske.krav.domain.Status
import no.nav.sokos.ske.krav.domain.StonadsType
import no.nav.sokos.ske.krav.service.Frontend
import no.nav.sokos.ske.krav.service.RapportService

@OptIn(Frontend::class)
class KravSomVenterPåOppryddingTemplateTest :
    BehaviorSpec({
        // Operatøren må se både hvorfor kravet er stoppet og en konkret handling for å
        // frigjøre akkurat den raden. Dette beskytter HTML-kontrakten mellom oversikten og Ktor-ruten.
        Given("et krav som venter på opprydding") {
            val krav =
                RapportService.RapportObjekt(
                    kravID = "42",
                    filnavn = "oppfolging.txt",
                    linjenummer = "1",
                    vedtaksId = "SAK-42",
                    vedtaksDato = "01.01.2026",
                    fagsystemId = "OB04",
                    kravkode = "PE AP",
                    kodeHjemmel = "T",
                    status = Status.PENDING_ENDRING_HOVEDSTOL,
                    stonadsType = StonadsType.entries.first(),
                    saksnummerNAV = "SAK-42",
                    referansenummerGammelSak = "SAK-41",
                    belop = 100.0,
                    periodeFOM = "20260101",
                    periodeTOM = "20260131",
                    feilmeldinger = emptyList(),
                    tidspunktSisteStatus = "01.01.2026 12:00",
                    kravidentifikatorSKE = "",
                    kravtype = "ENDRING_HOVEDSTOL",
                    belopRente = 0.0,
                    utbetaltDato = "",
                    tilleggsfrist = "",
                    fremtidigYtelse = 0.0,
                    gjelderId = "12345678901",
                )

            Then("skal vise pending-status og en posthandling for frigivelse") {
                val html =
                    createHTML().div {
                        insert(KravSomVenterPåOppryddingPart(listOf(krav))) {}
                    }

                html shouldContain "PENDING_ENDRING_HOVEDSTOL"
                html shouldContain """<form action="/rapporter/avventende-oppryddingskrav/release" method="post">"""
                html shouldContain """<input type="hidden" name="kravid" value="42">"""
                html shouldContain "Frigi"
            }
        }
    })
