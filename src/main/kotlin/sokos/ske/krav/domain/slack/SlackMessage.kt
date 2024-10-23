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

fun buildSlackMessage(
    feilHeader: String,
    filnavn: String,
    content: List<Pair<String, String>>,
): Data {
    val sections =
        buildList {
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

            addAll(
                content.map {
                    Block(
                        type = "section",
                        fields =
                            listOf(
                                Field(text = "*Feilmelding*\n${it.first}"),
                                Field(text = "*Info*\n${it.second}"),
                            ),
                    )
                },
            )

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
