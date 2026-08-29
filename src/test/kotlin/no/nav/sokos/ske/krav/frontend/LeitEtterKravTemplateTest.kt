package no.nav.sokos.ske.krav.frontend

import java.time.LocalDate
import java.time.LocalDateTime

import kotlinx.html.div
import kotlinx.html.stream.createHTML

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.server.html.insert

import no.nav.sokos.ske.krav.domain.Krav
import no.nav.sokos.ske.krav.domain.Status

class LeitEtterKravTemplateTest :
    BehaviorSpec({
        Given("Et krav med verdier i alle felt bortsett fra nullable felt") {
            val tidspunkt = LocalDateTime.parse("2026-08-29T12:30:45")
            val krav =
                Krav(
                    kravId = 123,
                    filnavn = "krav.txt",
                    linjenummer = 7,
                    kravidentifikatorSKE = "SKE-123",
                    saksnummerNAV = "SAK-456",
                    belop = 1000.5,
                    vedtaksDato = LocalDate.parse("2026-01-01"),
                    gjelderId = "12345678901",
                    periodeFOM = "202601",
                    periodeTOM = "202612",
                    kravkode = "K1",
                    referansenummerGammelSak = "GAMMEL-REF",
                    transaksjonsDato = "2026-02-01",
                    enhetBosted = "4806",
                    enhetBehandlende = "4807",
                    kodeHjemmel = "H1",
                    kodeArsak = "A1",
                    belopRente = 10.25,
                    fremtidigYtelse = 20.75,
                    utbetalDato = LocalDate.parse("2026-03-01"),
                    fagsystemId = "FSID-1",
                    status = Status.KRAV_IKKE_SENDT,
                    kravtype = "NYTT",
                    corrId = "corr-123",
                    tidspunktSendt = null,
                    tidspunktSisteStatus = tidspunkt,
                    tidspunktOpprettet = tidspunkt,
                    tilleggsfrist = null,
                    avsender = "OS",
                )

            val html =
                createHTML().div {
                    insert(FantKrav(krav)) {}
                }

            val etiketter = """<dt>(.*?)</dt>""".toRegex().findAll(html).map { it.groupValues[1] }.toList()

            Then("Skal alle Krav-felter vises med norske etiketter i alfabetisk rekkefølge") {
                val forventedeEtiketter =
                    listOf(
                        "Avsender",
                        "Beløp",
                        "Beløp rente",
                        "Corr-id",
                        "Enhet behandlende",
                        "Enhet bosted",
                        "Fagsystem-id",
                        "Filnavn",
                        "Fremtidig ytelse",
                        "Gjelder-id",
                        "Kode årsak",
                        "Kode hjemmel",
                        "Krav-id",
                        "Kravidentifikator SKE",
                        "Kravkode",
                        "Kravtype",
                        "Linjenummer",
                        "Periode FOM",
                        "Periode TOM",
                        "Referansenummer gammel sak",
                        "Saksnummer NAV",
                        "Status",
                        "Tilleggsfrist",
                        "Tidspunkt opprettet",
                        "Tidspunkt sendt",
                        "Tidspunkt siste status",
                        "Transaksjonsdato",
                        "Utbetalingsdato",
                        "Vedtaksdato",
                    )

                etiketter shouldBe forventedeEtiketter.sorted()
            }

            Then("Skal rendre nullable felter som 'Ikke satt'") {
                html shouldContain "<dd>Ikke satt</dd>"
                html.split("<dd>Ikke satt</dd>").size - 1 shouldBe 2
            }

            Then("Skal vise verdier for de øvrige feltene") {
                html shouldContain "<dd>123</dd>"
                html shouldContain "<dd>krav.txt</dd>"
                html shouldContain "<dd>7</dd>"
                html shouldContain "<dd>SKE-123</dd>"
                html shouldContain "<dd>SAK-456</dd>"
                html shouldContain "<dd>1000.5</dd>"
                html shouldContain "<dd>2026-01-01</dd>"
                html shouldContain "<dd>12345678901</dd>"
                html shouldContain "<dd>202601</dd>"
                html shouldContain "<dd>202612</dd>"
                html shouldContain "<dd>K1</dd>"
                html shouldContain "<dd>GAMMEL-REF</dd>"
                html shouldContain "<dd>2026-02-01</dd>"
                html shouldContain "<dd>4806</dd>"
                html shouldContain "<dd>4807</dd>"
                html shouldContain "<dd>H1</dd>"
                html shouldContain "<dd>A1</dd>"
                html shouldContain "<dd>10.25</dd>"
                html shouldContain "<dd>20.75</dd>"
                html shouldContain "<dd>2026-03-01</dd>"
                html shouldContain "<dd>FSID-1</dd>"
                html shouldContain "<dd>KRAV_IKKE_SENDT</dd>"
                html shouldContain "<dd>NYTT</dd>"
                html shouldContain "<dd>corr-123</dd>"
                html shouldContain "<dd>2026-08-29T12:30:45</dd>"
                html shouldContain "<dd>OS</dd>"
            }
        }
    })
