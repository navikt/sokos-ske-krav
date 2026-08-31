package no.nav.sokos.ske.krav.frontend

import kotlinx.html.div
import kotlinx.html.stream.createHTML

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.string.shouldContain

class AvstemmingTemplateTest :
    BehaviorSpec({
        Given("resend-knapp i avstemming") {
            val html =
                createHTML().div {
                    resendKravForm(
                        kravId = "123",
                        resendURL = "/rapporter/avstemming/resend",
                        resendBtnTitle = "Send på nytt",
                    )
                }

            Then("Skal poste kravid til resend-url med riktig knappetekst") {
                html shouldContain """<form action="/rapporter/avstemming/resend" method="post">"""
                html shouldContain """<input type="hidden" name="kravid" value="123">"""
                html shouldContain ">Send på nytt<"
            }
        }
    })
