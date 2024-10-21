package sokos.ske.krav.domain.slack

import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable

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
    val url: String? = null,
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
    content: List<List<String>>,
): Data {
    val sections = mutableListOf<Block>()
    with(sections) {
        add(
            Block(
                type = "header",
                text =
                    Text(
                        type = "plain_text",
                        text = ":error:  $feilHeader  ",
                        emoji = true,
                    ),
            ),
        )
        add(
            Block(type = "divider"),
        )
        add(
            Block(
                type = "section",
                fields =
                    listOf(
                        Field(
                            text = "*Filnavn* \n$filnavn",
                        ),
                        Field(
                            text = "*Dato* \n${Clock.System.now()}",
                        ),
                    ),
            ),
        )
        add(
            Block(type = "divider"),
        )
        addAll(content.map { section(it) })
        add(
            Block(type = "divider"),
        )
        add(
            Block(type = "divider"),
        )
    }
    return Data(
        text = ":package: $feilHeader",
        blocks = sections,
    )
}

private fun section(content: List<String>): Block {
    var label = "Feilmelding"
    return Block(
        type = "section",
        fields =
            content.map {
                val field =
                    Field(
                        text = "*$label*\n$it",
                    )
                label =
                    if (label == "Feilmelding") {
                        "Info"
                    } else {
                        "Feilmelding"
                    }
                field
            },
    )
}
