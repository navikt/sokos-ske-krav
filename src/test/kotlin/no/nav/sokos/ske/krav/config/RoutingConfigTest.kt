package no.nav.sokos.ske.krav.config

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class RoutingConfigTest :
    FunSpec({
        test("testkonsollen skal være tilgjengelig lokalt og i dev, men ikke i test eller prod") {
            isTestkonsollEnabled(Profile.LOCAL) shouldBe true
            isTestkonsollEnabled(Profile.DEV) shouldBe true
            isTestkonsollEnabled(Profile.DEV_GCP) shouldBe true
            isTestkonsollEnabled(Profile.TEST) shouldBe false
            isTestkonsollEnabled(Profile.PROD) shouldBe false
        }
    })
