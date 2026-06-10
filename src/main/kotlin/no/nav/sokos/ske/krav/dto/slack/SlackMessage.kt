package no.nav.sokos.ske.krav.dto.slack

import java.time.LocalDate

import kotlinx.serialization.Serializable

import no.nav.sokos.ske.krav.domain.TaggablePeople

@Serializable
data class Data(
    val text: String,
    val blocks: List<Block>,
)

@Serializable
data class Block(
    val type: String,
    val text: Text? = null,
    val fields: List<Field>? = null,
)

@Serializable
data class Text(
    val type: String = "plain_text",
    val text: String,
    val emoji: Boolean? = null,
)

@Serializable
data class Field(
    val type: String = "mrkdwn",
    val text: String,
)

fun createSlackMessage(
    feilHeader: String,
    filnavn: String,
    content: Map<String, List<String>>,
    taggedPeople: List<TaggablePeople> = emptyList(),
    rutineLink: String? = null,
    saksnummer: String = "",
) = Data(
    text = ":package: $feilHeader",
    blocks = buildSections(feilHeader, filnavn, content, taggedPeople, rutineLink, saksnummer),
)

private fun buildSections(
    feilHeader: String,
    filnavn: String,
    content: Map<String, List<String>>,
    taggedPeople: List<TaggablePeople>,
    rutineLink: String?,
    saksnummer: String,
): MutableList<Block> {
    val dividerBlock = Block(type = "divider")
    val headerBlock =
        Block(
            type = "header",
            text =
                Text(
                    type = "plain_text",
                    text = ":error:  $feilHeader  ",
                    emoji = true,
                ),
        )
    val filnavnBlock =
        Block(
            type = "section",
            fields =
                listOf(
                    Field(text = "*Filnavn* \n$filnavn"),
                    Field(text = "*Dato* \n${LocalDate.now()}"),
                    Field(text = "*Nav Saksnummer* \n$saksnummer"),
                ),
        )

    val feilmeldinger =
        content.flatMap { (errorType, errors) ->
            errors.map { error ->
                Block(
                    type = "section",
                    fields =
                        listOf(
                            Field(text = "*Feilmelding*\n$errorType"),
                            Field(text = "*Info*\n$error"),
                        ),
                )
            }
        }

    val blocks = mutableListOf<Block>()
    blocks.add(headerBlock)
    blocks.add(dividerBlock)
    blocks.add(filnavnBlock)
    blocks.add(dividerBlock)
    blocks.addAll(feilmeldinger)
    blocks.add(dividerBlock)

    if (taggedPeople.isNotEmpty()) {
        val tagText =
            buildString {
                append("*Ansvarlige:* ${taggedPeople.joinToString(" ") { it.slackId }}")
                if (rutineLink != null) {
                    append("\n*Rutine:* <$rutineLink|Klikk her for rutine>")
                }
            }
        blocks.add(Block(type = "section", text = Text(type = "mrkdwn", text = tagText)))
        blocks.add(dividerBlock)
    }

    return blocks
}
