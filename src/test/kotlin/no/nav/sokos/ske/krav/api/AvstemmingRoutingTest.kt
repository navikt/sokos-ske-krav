package no.nav.sokos.ske.krav.api

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class AvstemmingRoutingTest :
    BehaviorSpec({
        Given("saksnummerNav fra query-param") {
            Then("Skal trimmes og brukes når verdi finnes") {
                "  SAK-123  ".toTrimmedSaksnummerNav() shouldBe "SAK-123"
            }

            Then("Skal bli null når verdien er tom etter trimming") {
                "   ".toTrimmedSaksnummerNav() shouldBe null
                "".toTrimmedSaksnummerNav() shouldBe null
                null.toTrimmedSaksnummerNav() shouldBe null
            }
        }

        Given("redirect-path for krav-oppslag") {
            Then("Skal bygge /krav/{saksnummer} og URL-enkode path-delen") {
                kravLookupPath("SAK 123") shouldBe "/krav/SAK%20123"
            }
        }

        Given("valg av static-servering") {
            Then("Skal bruke filesystem-static kun lokalt") {
                shouldUseFilesystemStatic(isLocal = true) shouldBe true
                shouldUseFilesystemStatic(isLocal = false) shouldBe false
            }
        }
    })
