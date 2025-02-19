package sokos.ske.krav.service.unit

import io.kotest.core.spec.style.FunSpec
import io.kotest.data.blocking.forAll
import io.kotest.data.row
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.spyk
import sokos.ske.krav.client.SkeClient
import sokos.ske.krav.database.models.KravTable
import sokos.ske.krav.domain.Status
import sokos.ske.krav.domain.ske.requests.KravidentifikatorType
import sokos.ske.krav.service.DatabaseService
import sokos.ske.krav.service.EndreKravService
import sokos.ske.krav.util.RequestResult
import sokos.ske.krav.util.defineStatus
import sokos.ske.krav.util.mockHttpResponse

internal class EndreKravServiceTest :
    FunSpec({

        val databaseServiceMock =
            mockk<DatabaseService> {
                justRun { updateSentKrav(any()) }
            }

        val endreKravMock = spyk(EndreKravService(mockk<SkeClient>(), databaseServiceMock), recordPrivateCalls = true)
        val kravTableMock =
            mockk<KravTable> {
                every { kravidentifikatorSKE } returns "foo"
                every { saksnummerNAV } returns "bar"
            }

        forAll(
            row("404 and 422", 404, 422, Status.HTTP404_ANNEN_IKKE_FUNNET, Status.HTTP404_ANNEN_IKKE_FUNNET),
            row("409 and 422", 409, 422, Status.HTTP422_VALIDERINGSFEIL, Status.HTTP422_VALIDERINGSFEIL),
            row("409 and 404", 409, 404, Status.HTTP404_ANNEN_IKKE_FUNNET, Status.HTTP404_ANNEN_IKKE_FUNNET),
            row("409 and 200", 409, 200, Status.HTTP409_ANNEN_KONFLIKT, Status.HTTP409_ANNEN_KONFLIKT),
            row("200 and 422", 200, 422, Status.HTTP422_VALIDERINGSFEIL, Status.HTTP422_VALIDERINGSFEIL),
            row("102 and 102", 102, 102, Status.UKJENT_STATUS, Status.UKJENT_STATUS),
        ) { _, firstStatus, secondStatus, expectedFirstStatus, expectedSecondStatus ->

            test("If first status is $firstStatus and second status is $secondStatus, both should be set to $expectedFirstStatus") {

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
