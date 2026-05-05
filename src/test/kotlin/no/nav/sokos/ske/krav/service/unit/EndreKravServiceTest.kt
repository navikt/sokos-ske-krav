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
import no.nav.sokos.ske.krav.dto.ske.requests.KravidentifikatorType
import no.nav.sokos.ske.krav.service.EndreKravService
import no.nav.sokos.ske.krav.util.RequestResult
import no.nav.sokos.ske.krav.util.defineStatus

internal class EndreKravServiceTest :
    FunSpec({

        val endreKravMock = spyk(EndreKravService(mockk<SkeClient>()), recordPrivateCalls = true)
        val kravMock =
            mockk<Krav> {
                every { kravidentifikatorSKE } returns "foo"
                every { saksnummerNAV } returns "bar"
            }

        data class TestCase(
            val description: String,
            val firstStatus: Int,
            val secondStatus: Int,
            val expectedFirstStatus: Status,
            val expectedSecondStatus: Status,
        )
        listOf(
            TestCase("404 and 422", 404, 422, Status.HTTP404_ANNEN_IKKE_FUNNET, Status.HTTP404_ANNEN_IKKE_FUNNET),
            TestCase("409 and 422", 409, 422, Status.HTTP422_VALIDERINGSFEIL, Status.HTTP422_VALIDERINGSFEIL),
            TestCase("409 and 404", 409, 404, Status.HTTP404_ANNEN_IKKE_FUNNET, Status.HTTP404_ANNEN_IKKE_FUNNET),
            TestCase("409 and 200", 409, 200, Status.HTTP409_ANNEN_KONFLIKT, Status.HTTP409_ANNEN_KONFLIKT),
            TestCase("200 and 422", 200, 422, Status.HTTP422_VALIDERINGSFEIL, Status.HTTP422_VALIDERINGSFEIL),
            TestCase("102 and 102", 102, 102, Status.UKJENT_STATUS, Status.UKJENT_STATUS),
        ).forEach { (_: String, firstStatus: Int, secondStatus: Int, expectedFirstStatus: Status, expectedSecondStatus: Status) ->

            test("If first status is $firstStatus and second status is $secondStatus, both should be set to $expectedFirstStatus") {

                val firstStatusCode = HttpStatusCode.fromValue(firstStatus)
                val secondStatusCode = HttpStatusCode.fromValue(secondStatus)
                every {
                    endreKravMock["sendEndreKrav"](any<String>(), any<KravidentifikatorType>(), any<Krav>())
                } returnsMany
                    if (firstStatus == 102 && secondStatus == 102) {
                        listOf(
                            RequestResult("", firstStatusCode, mockk<Krav>(), "", "", Status.HTTP409_KRAV_ER_AVSKREVET),
                            RequestResult("", secondStatusCode, mockk<Krav>(), "", "", Status.HTTP500_ANNEN_SERVER_FEIL),
                        )
                    } else {
                        listOf(
                            RequestResult("", firstStatusCode, mockk<Krav>(), "", "", defineStatus("", firstStatusCode).first),
                            RequestResult("", secondStatusCode, mockk<Krav>(), "", "", defineStatus("", secondStatusCode).first),
                        )
                    }

                val result = endreKravMock.sendAllEndreKrav(listOf(kravMock, kravMock))

                result[0].status shouldBe expectedFirstStatus
                result[1].status shouldBe expectedSecondStatus
            }
        }

        test("getConformedResponses should return requestResults unchanged when size is 0") {
            every {
                endreKravMock["sendEndreKrav"](any<String>(), any<KravidentifikatorType>(), any<Krav>())
            } returns RequestResult("", HttpStatusCode.OK, mockk<Krav>(), "", "", Status.KRAV_SENDT)

            // Send empty list, which should result in empty request results
            val result = endreKravMock.sendAllEndreKrav(emptyList())

            result.size shouldBe 0
        }

        test("getConformedResponses should return requestResults unchanged when size is 1") {
            val expectedStatus = Status.KRAV_SENDT
            every {
                endreKravMock["sendEndreKrav"](any<String>(), any<KravidentifikatorType>(), any<Krav>())
            } returns RequestResult("", HttpStatusCode.OK, mockk<Krav>(), "", "", expectedStatus)

            val result = endreKravMock.sendAllEndreKrav(listOf(kravMock))

            result.size shouldBe 1
            result[0].status shouldBe expectedStatus
        }
    })
