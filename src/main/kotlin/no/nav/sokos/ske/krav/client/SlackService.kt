package no.nav.sokos.ske.krav.client

internal data class ErrorHeader(
    val header: String,
    val errors: MutableMap<String, MutableList<String>>,
)

internal data class FileErrors(
    val fileName: String,
    val headers: MutableList<ErrorHeader>,
)

enum class Tags(
    val personer: List<String>,
    val rutineLink: String? = null,
) {
    PERSON_EKSISTERER_IKKE(listOf("@lene.johannessen", "@trine.johansen")),
    PERSON_ER_DOED(listOf("@lene.johannessen", "@trine.johansen")),
    ORGANISASJONSNUMMER_FINNES_IKKE(listOf("@lene.johannessen", "@trine.johansen")),
    ORGANISASJON_ER_OPPHOERT(
        listOf("@marita.ragnvaldsdatt.karlsen", "@line.anita.edvardsen", "@steinar.hansen"),
        "https://confluence.adeo.no/spaces/TOB/pages/791026050/Rutine+for+manuell+h%C3%A5ndtering+av+innkrevingskrav+til+skatteetaten+SKE",
    ),
    PERSON_ER_SLETTET(listOf("@lene.johannessen", "@trine.johansen")),
    ORGANISASJON_ER_SLETTET(listOf("@lene.johannessen", "@trine.johansen")),
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
