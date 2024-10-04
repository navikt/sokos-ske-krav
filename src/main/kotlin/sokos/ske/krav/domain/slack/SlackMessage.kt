package sokos.ske.krav.domain.slack

import kotlinx.serialization.Serializable

@Serializable
data class Data(
    val blocks: List<Block>,
)

@Serializable
data class Block(
    val type: String,
    val text: Text? = null,
    val fields: List<Field>? = null,
    val accessory: Accessory? = null,
    val elements: List<Elements>? = null,
)

@Serializable
data class Accessory(
    val type: String,
    val text: Text?,
    val value: String? = null,
    val style: String? = null,
    val action_id: String? = null,
)

@Serializable
data class Elements(
    val type: String,
    val text: Text?,
    val value: String? = null,
    val style: String? = null,
    val action_id: String? = null,
)

@Serializable
data class Text(
    val type: String = "plain_text",
    val text: String,
    val emoji: Boolean = false,
)

@Serializable
data class Field(
    val type: String = "mrkdwn",
    val text: String,
)

fun message(
    feilHeader: String,
    filnavn: String,
    content: List<List<String>>
): Data {
    val sections = mutableListOf<Block>()
    with(sections) {
        add(
            Block(
                type = "header",
                text = Text(
                    type = "plain_text",
                    text = "$feilHeader",
                    emoji = true
                )
            )
        )
        add(
            Block(
                type = "section",
                fields = listOf(
                    Field(
                        text = "*Filnavn*\n${filnavn}"
                    )
                )
            )
        )
        addAll(content.map { section(it) })
    }
    return Data(sections)
}

private fun section(content: List<String>): Block {
    var label: String = "Feilmelding"
    return Block(
        type = "section",
        fields = content.map {
            val field = Field(
                text = "*$label*\n${it}"
            )
            if (label.equals("Feilmelding")) {
                label = "Info"
            } else {
                label = "Feilmelding"
            }
            field
        }
    )
}

