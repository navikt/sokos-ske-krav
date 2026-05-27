package no.nav.sokos.ske.krav.service.unit

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk

import no.nav.sokos.ske.krav.client.SkeClient
import no.nav.sokos.ske.krav.domain.Krav
import no.nav.sokos.ske.krav.domain.Status
import no.nav.sokos.ske.krav.service.StoppKravService
import no.nav.sokos.ske.krav.util.RequestResult

class StoppKravServiceTest :
    FunSpec({

        test("sendAllStoppKrav skal returnere liste av innsendte stopp av krav") {
            val kravMock =
                mockk<Krav> {
                    every { kravidentifikatorSKE } returns "foo"
                    every { saksnummerNAV } returns "bar"
                }
            val stoppKravMock = spyk(StoppKravService(mockk<SkeClient>()), recordPrivateCalls = true)

            every { stoppKravMock["sendStoppKrav"](any<Krav>()) } returnsMany
                listOf(
                    RequestResult("", HttpStatusCode.UnprocessableEntity, mockk<Krav>(), "", "123", Status.HTTP422_VALIDERINGSFEIL),
                    RequestResult("", HttpStatusCode.OK, mockk<Krav>(), "", "456", Status.KRAV_SENDT),
                )
            val result = stoppKravMock.sendAllStoppKrav(listOf(kravMock, kravMock))

            result.size shouldBe 2
            result.filter { it.kravidentifikator == "123" }.size shouldBe 1
            result.filter { it.kravidentifikator == "456" }.size shouldBe 1
        }
    })
