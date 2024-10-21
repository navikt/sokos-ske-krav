package sokos.ske.krav.service.unit

import io.kotest.core.annotation.Ignored
import io.kotest.core.spec.style.FunSpec
import io.mockk.coEvery
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import sokos.ske.krav.database.models.KravTable
import sokos.ske.krav.domain.nav.KravLinje
import sokos.ske.krav.service.DatabaseService
import sokos.ske.krav.service.EndreKravService
import sokos.ske.krav.service.OpprettKravService
import sokos.ske.krav.service.StoppKravService
import sokos.ske.krav.util.RequestResult
import sokos.ske.krav.util.defineStatus
import sokos.ske.krav.util.mockHttpResponse
import sokos.ske.krav.util.setupSkeServiceMock

/*
* Enhetstester for SkeService. Funksjonskallene er kommentert ut siden de er private. Gjør funksjonene public når du skal teste.
* */
@Ignored
internal class SkeServiceTest : FunSpec({

    test("sendKrav skal returnere RequestResults fra opprett/endre/stopp service") {
        val stoppServiceMock =
            mockk<StoppKravService> {
                coEvery { sendAllStoppKrav(any()) } returns
                    listOf(
                        RequestResult(
                            mockHttpResponse(200),
                            mockk<KravTable>(),
                            "",
                            "STOPP_KRAV",
                            defineStatus(mockHttpResponse(200)),
                        ),
                    )
            }

        val endreServiceMock =
            mockk<EndreKravService> {
                coEvery { sendAllEndreKrav(any()) } returns
                    listOf(
                        RequestResult(mockHttpResponse(200), mockk<KravTable>(), "", "ENDRE_RENTER", defineStatus(mockHttpResponse(200))),
                        RequestResult(mockHttpResponse(200), mockk<KravTable>(), "", "ENDRE_HOVEDSTOL", defineStatus(mockHttpResponse(200))),
                    )
            }

        val opprettServiceMock =
            mockk<OpprettKravService> {
                coEvery { sendAllOpprettKrav(any()) } returns (
                    listOf(
                        RequestResult(
                            mockHttpResponse(200),
                            mockk<KravTable>(),
                            "",
                            "NYTT_KRAV",
                            defineStatus(mockHttpResponse(200)),
                        ),
                    )
                )
            }

        val skeService =
            setupSkeServiceMock(
                stoppService = stoppServiceMock,
                endreService = endreServiceMock,
                opprettService = opprettServiceMock,
            )

        /*     val result = skeService.sendKrav(FtpFil("foo", emptyList(), emptyList()))
             result.size shouldBe 4
             result.filter { it.kravIdentifikator == "STOPP_KRAV" }.size shouldBe 1
             result.filter { it.kravIdentifikator == "ENDRE_RENTER" }.size shouldBe 1
             result.filter { it.kravIdentifikator == "ENDRE_HOVEDSTOL" }.size shouldBe 1
             result.filter { it.kravIdentifikator == "NYTT_KRAV" }.size shouldBe 1*/
    }

    test("resendKrav skal returnere et map av kravtype og RequestResults fra opprett/endre/stopp service der det har gått galt") {
        val stoppServiceMock =
            mockk<StoppKravService> {
                coEvery { sendAllStoppKrav(any()) } returns
                    listOf(
                        RequestResult(
                            mockHttpResponse(404),
                            mockk<KravTable>(),
                            "",
                            "STOPP_KRAV",
                            defineStatus(mockHttpResponse(404)),
                        ),
                    )
            }

        val endreServiceMock =
            mockk<EndreKravService> {
                coEvery { sendAllEndreKrav(any()) } returns
                    listOf(
                        RequestResult(mockHttpResponse(200), mockk<KravTable>(), "", "ENDRE_RENTER", defineStatus(mockHttpResponse(200))),
                        RequestResult(mockHttpResponse(200), mockk<KravTable>(), "", "ENDRE_HOVEDSTOL", defineStatus(mockHttpResponse(200))),
                    )
            }

        val opprettServiceMock =
            mockk<OpprettKravService> {
                coEvery { sendAllOpprettKrav(any()) } returns
                    listOf(
                        RequestResult(
                            mockHttpResponse(500),
                            mockk<KravTable>(),
                            "",
                            "NYTT_KRAV",
                            defineStatus(mockHttpResponse(500)),
                        ),
                    )
            }

        val dataSourceMock =
            mockk<DatabaseService> {
                every { getAllUnsentKrav() } returns emptyList()
                justRun { saveAllNewKrav(any<List<KravLinje>>(), "filnavn.txt") }
                every { getSkeKravidentifikator(any<String>()) } returns "foo"
            }

        val skeService =
            setupSkeServiceMock(
                stoppService = stoppServiceMock,
                endreService = endreServiceMock,
                opprettService = opprettServiceMock,
            )

        /*  val resultMap = skeService.resendKrav()

          resultMap.size shouldBe 2
          resultMap shouldContainKey STOPP_KRAV
          resultMap shouldContainKey NYTT_KRAV
          resultMap shouldNotContainKey ENDRE_RENTER
          resultMap shouldNotContainKey ENDRE_HOVEDSTOL
          resultMap[STOPP_KRAV]?.kravIdentifikator shouldBe "STOPP_KRAV"
          resultMap[NYTT_KRAV]?.kravIdentifikator shouldBe "NYTT_KRAV"*/
    }
})
