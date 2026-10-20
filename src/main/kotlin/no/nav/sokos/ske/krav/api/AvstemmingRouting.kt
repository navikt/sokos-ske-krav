package no.nav.sokos.ske.krav.api

import kotlinx.coroutines.CancellationException
import kotlinx.io.readByteArray

import io.ktor.http.ContentType.Text.CSV
import io.ktor.http.content.MultiPartData
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.encodeURLPathPart
import io.ktor.http.encodeURLQueryComponent
import io.ktor.server.auth.principal
import io.ktor.server.html.respondHtmlTemplate
import io.ktor.server.http.content.staticResources
import io.ktor.server.request.receiveMultipart
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.utils.io.readRemaining
import mu.KotlinLogging

import no.nav.sokos.ske.krav.config.PropertiesConfig
import no.nav.sokos.ske.krav.config.TEAM_LOGS_MARKER
import no.nav.sokos.ske.krav.config.isTestkonsollEnabled
import no.nav.sokos.ske.krav.domain.Krav
import no.nav.sokos.ske.krav.frontend.AdminPage
import no.nav.sokos.ske.krav.frontend.FantIngenting
import no.nav.sokos.ske.krav.frontend.FantKrav
import no.nav.sokos.ske.krav.frontend.FilnavnKravSokPage
import no.nav.sokos.ske.krav.frontend.IngentingEnda
import no.nav.sokos.ske.krav.frontend.LeitEtterKravPage
import no.nav.sokos.ske.krav.frontend.RapportPage
import no.nav.sokos.ske.krav.frontend.SendUsendtePage
import no.nav.sokos.ske.krav.frontend.TestkonsollPage
import no.nav.sokos.ske.krav.service.Frontend
import no.nav.sokos.ske.krav.service.RapportService
import no.nav.sokos.ske.krav.service.RapportType
import no.nav.sokos.ske.krav.service.SkeService
import no.nav.sokos.ske.krav.testconsole.TestkonsollService

private val logger = KotlinLogging.logger { }

@OptIn(Frontend::class)
fun Route.avstemmingRoutes(
    skeService: SkeService,
    rapportService: RapportService = RapportService(),
    testkonsollService: TestkonsollService,
) {
    staticResources("/static", "static")

    route("") {
        get { call.respondHtmlTemplate(AdminPage(call)) {} }
    }

    if (isTestkonsollEnabled()) {
        route("testing") {
            get {
                val feedback = call.request.queryParameters["result"]
                call.respondHtmlTemplate(TestkonsollPage(call, feedback)) {}
            }
            post("/last-opp-fil") {
                call.runTestAction("laste opp fil") {
                    val uploadedFile = call.receiveMultipart().readUploadedFile()
                    testkonsollService.lastOppFil(uploadedFile.first, uploadedFile.second)
                }
            }
            post("/behandle-krav") { call.runTestAction("behandle krav", testkonsollService::behandleKrav) }
            post("/hent-mottaksstatus") { call.runTestAction("hente mottaksstatus", testkonsollService::hentMottaksstatus) }
        }
    }

    route("rapporter") {
        route("avstemming") {
            get {
                call.respondHtmlTemplate(RapportPage(RapportType.AVSTEMMING, call)) {
                    title { +"Innkrevingsoppdrag med feil" }
                    avstemmingContent { }
                }
            }
            post("/update") {
                val id = call.receiveParameters()["kravid"]
                if (!id.isNullOrBlank()) {
                    val innloggetBruker = call.principal<io.ktor.server.auth.jwt.JWTPrincipal>()?.get("preferred_username")
                    logger.info(marker = TEAM_LOGS_MARKER) { "$innloggetBruker oppdaterer status til rapportert for krav: $id" }
                    logger.info { "Oppdaterer status til rapportert for krav: $id" }
                    rapportService.oppdaterStatusTilRapportert(id.toInt())
                }
                call.respondRedirect("/rapporter/avstemming")
            }
            post("/resend") {
                val id = call.receiveParameters()["kravid"]?.toIntOrNull()
                if (id != null) {
                    val innloggetBruker = call.principal<io.ktor.server.auth.jwt.JWTPrincipal>()?.get("preferred_username")
                    logger.info(marker = TEAM_LOGS_MARKER) { "$innloggetBruker oppdaterer status til KRAV_IKKE_SENDT for krav: $id" }
                    logger.info { "Oppdaterer status til KRAV_IKKE_SENDT for krav: $id" }
                    rapportService.oppdaterStatusTilIkkeSendt(id)
                }
                call.respondRedirect("/rapporter/avstemming")
            }
            post("/CSVdownload") {
                val csv = call.receiveParameters()["csv"].toString()
                call.respondText(csv, CSV)
            }
            get("/") { call.respondRedirect("/rapporter/avstemming") }
        }
        route("resending") {
            get {
                call.respondHtmlTemplate(RapportPage(RapportType.RESENDING, call)) {
                    title { +"Krav Som skal resendes" }
                    resendingContent { }
                }
            }
            get("/") { call.respondRedirect("/rapporter/resending") }
        }
        route("resendUsendte") {
            get {
                call.respondHtmlTemplate(SendUsendtePage(call)) {
                }
            }
            get("/") { call.respondRedirect("/rapporter/resendUsendte") }
            post("/sendUnsent") {
                val filer = call.receiveParameters()["filer"]?.toIntOrNull() ?: 5
                val innloggetBruker = call.principal<io.ktor.server.auth.jwt.JWTPrincipal>()?.get("preferred_username")
                logger.info(marker = TEAM_LOGS_MARKER) { "$innloggetBruker sender usendte filer, max: $filer" }
                logger.info { "Sender usendte filer, max: $filer" }
                println("Sender usendte filer, max: $filer")
                val sendteFiler = skeService.sendUsendteFiler(filer)
                call.respondText("Sendte $sendteFiler filer")
            }
        }
    }
    route("/krav") {
        get("/filnavn") {
            val filnavn =
                call.request.queryParameters["filnavn"]
                    ?.trim()
                    .orEmpty()
            val krav = if (filnavn.isBlank()) emptyList() else rapportService.finnKravForFilnavn(filnavn)
            call.respondHtmlTemplate(FilnavnKravSokPage(krav, filnavn, call)) {}
        }
        get("") {
            val leiteparameter = call.request.queryParameters["leiteparameter"].toTrimmedSaksnummerNav()
            if (leiteparameter != null) {
                call.respondRedirect(kravLookupPath(leiteparameter))
                return@get
            }
            call.respondHtmlTemplate(LeitEtterKravPage(IngentingEnda, call)) {
            }
        }
        get("/") { call.respondRedirect("/krav") }
        get("/{leiteparameter}") {
            val leiteparameter = call.parameters["leiteparameter"] ?: ""
            val content =
                when (val krav = rapportService.finnKrav(leiteparameter)) {
                    emptyList<Krav>() -> FantIngenting(leiteparameter)
                    else -> FantKrav(krav)
                }
            call.respondHtmlTemplate(LeitEtterKravPage(content, call)) {
            }
        }
    }
}

private suspend fun MultiPartData.readUploadedFile(): Pair<String, ByteArray> {
    var uploadedFile: Pair<String, ByteArray>? = null
    forEachPart { part ->
        if (part is PartData.FileItem && part.name == "fil" && uploadedFile == null) {
            val filnavn = part.originalFileName ?: throw IllegalArgumentException("Filen mangler filnavn.")
            uploadedFile = filnavn to part.provider().readRemaining().readByteArray()
        }
        part.release()
    }
    return uploadedFile ?: throw IllegalArgumentException("Velg en fil før opplasting.")
}

private suspend fun io.ktor.server.application.ApplicationCall.runTestAction(
    action: String,
    run: suspend () -> TestkonsollService.ActionResult,
) {
    val innloggetBruker = principal<io.ktor.server.auth.jwt.JWTPrincipal>()?.get("preferred_username")
    logger.info(marker = TEAM_LOGS_MARKER) { "$innloggetBruker starter manuell handling: $action" }
    val result =
        try {
            run().message
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error(marker = TEAM_LOGS_MARKER) { "$innloggetBruker fikk feil under manuell handling: $action. ${e.message}" }
            "Handlingen feilet. Se applikasjonsloggene for detaljer."
        }
    if (!result.startsWith("Handlingen feilet")) {
        logger.info { "Manuell handling fullført: $action. $result" }
    }
    respondRedirect("/testing?result=${result.encodeURLQueryComponent()}")
}

internal fun String?.toTrimmedSaksnummerNav(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

internal fun kravLookupPath(saksnummer: String): String = "/krav/${saksnummer.encodeURLPathPart()}"

internal fun shouldUseFilesystemStatic(isLocal: Boolean = PropertiesConfig.isLocal): Boolean = isLocal
