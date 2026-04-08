package no.nav.sokos.ske.krav.client

internal data class ErrorHeader(
    val header: String,
    val errors: MutableMap<String, MutableList<String>>,
)

internal data class FileErrors(
    val fileName: String,
    val headers: MutableList<ErrorHeader>,
)

private const val LENE = "<@U08S6FA0XSS>"
private const val TRINE = "<@UDCM6F8V8>"
private const val MARITA = "<@UCG179DPT>"
private const val LINE_ANITA = "<@U02AVNPT3T9>"
private const val STEINAR = "<@U796MGBA9>"

private enum class Tags(
    val personer: List<String>,
    val rutineLink: String? = null,
) {
    PERSON_EKSISTERER_IKKE(listOf(LENE, TRINE)),
    PERSON_ER_DOED(listOf(LENE, TRINE)),
    ORGANISASJONSNUMMER_FINNES_IKKE(listOf(LENE, TRINE)),
    ORGANISASJON_ER_OPPHOERT(
        listOf(MARITA, LINE_ANITA, STEINAR),
        "https://confluence.adeo.no/spaces/TOB/pages/791026050/Rutine+for+manuell+h%C3%A5ndtering+av+innkrevingskrav+til+skatteetaten+SKE",
    ),
    PERSON_ER_SLETTET(listOf(LENE, TRINE)),
    ORGANISASJON_ER_SLETTET(listOf(LENE, TRINE)),
}

class SlackService(
    private val slackClient: SlackClient = SlackClient(),
) {
    private val errorTracking: MutableList<FileErrors> = mutableListOf()

    fun addError(
        fileName: String,
        header: String,
        messages: Map<String, List<String>>,
    ) {
        val fileError =
            errorTracking.find { it.fileName == fileName }
                ?: FileErrors(fileName, mutableListOf()).also { errorTracking.add(it) }

        val headerEntry =
            fileError.headers.find { it.header == header }
                ?: ErrorHeader(header, mutableMapOf()).also { fileError.headers.add(it) }

        messages.forEach { (errorType, errorMessages) ->
            val errorTypeMessages = headerEntry.errors.getOrPut(errorType) { mutableListOf() }
            errorTypeMessages.addAll(errorMessages)
        }
    }

    fun addError(
        fileName: String,
        header: String,
        messages: Pair<String, String>,
    ) {
        val map = mapOf(messages.first to listOf(messages.second))
        addError(fileName, header, map)
    }

    fun addError(
        fileName: String,
        header: String,
        messages: List<Pair<String, String>>,
    ) {
        val map = messages.groupBy({ it.first }, { it.second })
        addError(fileName, header, map)
    }

    private fun consolidateErrors() {
        errorTracking.forEach { fileErrors ->
            fileErrors.headers.forEach { header ->
                header.errors.forEach { (errorType, messages) ->
                    if (messages.size > 5) {
                        header.errors[errorType] =
                            mutableListOf(
                                "${messages.size} av samme type feil: $errorType. Sjekk avstemming",
                            )
                    }
                }
            }
        }
    }

    suspend fun sendErrors() {
        consolidateErrors()
        errorTracking.forEach { fileErrors ->
            fileErrors.headers.forEach { header ->
                val matchedTags = header.errors.keys.mapNotNull { errorType -> Tags.entries.find { it.name == errorType } }
                val taggedPeople = matchedTags.flatMap { it.personer }.distinct()
                val rutineLink = matchedTags.firstNotNullOfOrNull { it.rutineLink }
                slackClient.sendMessage(header.header, fileErrors.fileName, header.errors, taggedPeople, rutineLink)
            }
        }

        errorTracking.clear()
    }
}
