package no.nav.sokos.ske.krav.service.unit

import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.spyk

import no.nav.sokos.ske.krav.client.SkeClient
import no.nav.sokos.ske.krav.database.models.KravTable
import no.nav.sokos.ske.krav.domain.Status
import no.nav.sokos.ske.krav.domain.ske.requests.KravidentifikatorType
import no.nav.sokos.ske.krav.service.DatabaseService
import no.nav.sokos.ske.krav.service.EndreKravService
import no.nav.sokos.ske.krav.util.RequestResult
import no.nav.sokos.ske.krav.util.defineStatus
import no.nav.sokos.ske.krav.util.mockHttpResponse

internal class EndreKravServiceTest :
    FunSpec({

        val databaseServiceMock =
            mockk<DatabaseService> {
                justRun { updateSentKrav(any<List<RequestResult>>()) }
            }

        val endreKravMock = spyk(EndreKravService(mockk<SkeClient>(), databaseServiceMock), recordPrivateCalls = true)
        val kravTableMock =
            mockk<KravTable> {
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

        withData(
            listOf(
                TestCase("404 and 422", 404, 422, Status.HTTP404_ANNEN_IKKE_FUNNET, Status.HTTP404_ANNEN_IKKE_FUNNET),
                TestCase("409 and 422", 409, 422, Status.HTTP422_VALIDERINGSFEIL, Status.HTTP422_VALIDERINGSFEIL),
                TestCase("409 and 404", 409, 404, Status.HTTP404_ANNEN_IKKE_FUNNET, Status.HTTP404_ANNEN_IKKE_FUNNET),
                TestCase("409 and 200", 409, 200, Status.HTTP409_ANNEN_KONFLIKT, Status.HTTP409_ANNEN_KONFLIKT),
                TestCase("200 and 422", 200, 422, Status.HTTP422_VALIDERINGSFEIL, Status.HTTP422_VALIDERINGSFEIL),
                TestCase("102 and 102", 102, 102, Status.UKJENT_STATUS, Status.UKJENT_STATUS),
            ),
        ) { testCase ->
            val (description, firstStatus, secondStatus, expectedFirstStatus, expectedSecondStatus) = testCase

            test(description) {
                every {
                    endreKravMock["sendEndreKrav"](any<String>(), any<KravidentifikatorType>(), any<KravTable>())
                } returnsMany
                    if (firstStatus == 102 && secondStatus == 102) {
                        listOf(
                            RequestResult(mockHttpResponse(102), mockk<KravTable>(), "", "", Status.HTTP409_KRAV_ER_AVSKREVET),
                            RequestResult(mockHttpResponse(102), mockk<KravTable>(), "", "", Status.HTTP500_ANNEN_SERVER_FEIL),
                        )
                    } else {
                        listOf(
                            RequestResult(mockHttpResponse(firstStatus), mockk<KravTable>(), "", "", defineStatus(mockHttpResponse(firstStatus))),
                            RequestResult(mockHttpResponse(secondStatus), mockk<KravTable>(), "", "", defineStatus(mockHttpResponse(secondStatus))),
                        )
                    }

                val result = endreKravMock.sendAllEndreKrav(listOf(kravTableMock, kravTableMock))

                result[0].status shouldBe expectedFirstStatus
                result[1].status shouldBe expectedSecondStatus
            }
        }
    })
