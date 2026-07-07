package no.nav.sokos.ske.krav.dto.slack

import java.time.LocalDate

import kotlinx.serialization.Serializable

import no.nav.sokos.ske.krav.dto.slack.BlockType.DIVIDER
import no.nav.sokos.ske.krav.dto.slack.BlockType.HEADER
import no.nav.sokos.ske.krav.dto.slack.BlockType.SECTION
import no.nav.sokos.ske.krav.validation.ErrorCategory

private const val TEXT_TYPE_PLAIN = "plain_text"
private const val TEXT_TYPE_MRKDWN = "mrkdwn"
private const val EMOJI_PACKAGE = ":package:"
private const val EMOJI_ERROR = ":error:"
private const val FILENAME_HEADER = "*Filnavn*"
private const val DATE_HEADER = "*Dato*"
private const val ERROR_HEADER = "*Feilmelding*"
private const val INFO_HEADER = "*Info*"
private const val CASE_NUMBER_HEADER = "*Saksnummer:*"
private const val RESPONSIBLE_HEADER = "*Ansvarlige:*"
private const val ROUTINE_LINK_HEADER = "*Rutine*"
private const val ROUTINE_LINK_TEXT = "Klikk her for rutine"

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
    val text: String,
    val type: String = TEXT_TYPE_PLAIN,
    val emoji: Boolean? = null,
)

@Serializable
data class Field(
    val text: String,
    val type: String = TEXT_TYPE_MRKDWN,
)

enum class BlockType {
    DIVIDER,
    HEADER,
    SECTION,
    ;

    override fun toString(): String = name.lowercase()
}

private val dividerBlock = Block(DIVIDER.toString())

fun createSlackMessage(
    alertTitle: ErrorCategory,
    filename: String,
    extraTags: ExtraTags,
    errorDetails: List<ErrorDetails>,
): Data {
    val blocks =
        buildList {
            add(headerBlock(alertTitle))
            add(dividerBlock)
            add(filenameBlock(filename))
            add(dividerBlock)

            errorDetails.forEach {
                add(errorBlock(it))
            }

            add(dividerBlock)
            add(extraTagsBlock(extraTags))
            add(dividerBlock)
        }

    return Data("$EMOJI_PACKAGE ${alertTitle.value}", blocks)
}

fun headerBlock(alertTitle: ErrorCategory) =
    Block(
        type = HEADER.toString(),
        text =
            Text(
                type = TEXT_TYPE_PLAIN,
                text = "$EMOJI_ERROR $alertTitle",
                emoji = true,
            ),
    )

fun filenameBlock(filename: String) =
    Block(
        type = SECTION.toString(),
        fields =
            listOf(
                Field("$FILENAME_HEADER \n$filename"),
                Field("$DATE_HEADER \n${LocalDate.now()}"),
            ),
    )

fun errorBlock(errorDetails: ErrorDetails) =
    Block(
        type = SECTION.toString(),
        fields =
            listOf(
                Field("$ERROR_HEADER \n${errorDetails.header}"),
                Field("$INFO_HEADER \n${errorDetails.description}\n$CASE_NUMBER_HEADER ${errorDetails.caseNumber ?: "Ingen"}"),
            ),
    )

fun extraTagsBlock(extraTags: ExtraTags): Block {
    val txt =
        buildString {
            append("$RESPONSIBLE_HEADER ${extraTags.people.joinToString(" ") { it.slackId }}")
            extraTags.rutineLink.forEach {
                append("\n$ROUTINE_LINK_HEADER <$it|$ROUTINE_LINK_TEXT>")
            }
        }

    return Block(
        type = SECTION.toString(),
        text =
            Text(
                text = txt,
                type = TEXT_TYPE_MRKDWN,
            ),
    )
}
