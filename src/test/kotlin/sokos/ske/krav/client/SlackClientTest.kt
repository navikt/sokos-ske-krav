package sokos.ske.krav.client

import com.google.gson.GsonBuilder
import io.kotest.core.spec.style.FunSpec
import sokos.ske.krav.domain.slack.Block
import sokos.ske.krav.domain.slack.Data
import sokos.ske.krav.domain.slack.Elements
import sokos.ske.krav.domain.slack.Field
import sokos.ske.krav.domain.slack.Text
import sokos.ske.krav.domain.slack.message

class SlackClientTest : FunSpec({

    test("TEster om jeg får melding til slack").config(enabled = false) {

        val sk = SlackClient()
        val data =
            Data(
                text = "hei hei",
                listOf(
                    Block(
                        type = "header",
                        text = Text(text = "FEILMELDING"),
                    ),
                    Block(
                        type = "section",
                        fields =
                            listOf(
                                Field(
                                    text = "*FEIL1*:\nFeil nummer 1",
                                ),
                            ),
                    ),
                    Block(
                        type = "section",
                        fields =
                            listOf(
                                Field(
                                    text = "*FEIL2*:\nFeil nummer 2 men hvor lange kan disse meldingene være før de brekker uansett når det kun er en feilkollone i seksjonene over",
                                ),
                            ),
                    ),
                    Block(
                        type = "actions",
                        elements =
                            listOf(
                                Elements(
                                    type = "button",
                                    text =
                                        Text(
                                            type = "plain_text",
                                            text = "Click me",
                                        ),
                                    value = "Click_value",
                                    action_id = "button_action",
                                ),
                            ),
                    ),
                ),
            )

        val gson = GsonBuilder().setPrettyPrinting().create()
        println(gson.toJson(data))
        sk.doPost(data).also { println("Response: ${it.status}") }
    }

    test("tester ny oppbygging av melding").config(enabled = false) {
        val listList =
            listOf(
                listOf("Feil i innsending av krav", "Antall krav stemmer ikke med antallet i siste linje! Antall krav:16, Antall i siste linje: 11101"),
                listOf("Feil i validering av linje", "Kravtype finnes ikke definert for oversending til skatt : (FO FT sammen med (EU) på linje 4995 ] "),
            )

        val data = message("Tester Slack ved kjøring av sokos-ske-krav", "Feilfil1.txt", listList)
        val gson = GsonBuilder().setPrettyPrinting().create()
        println(gson.toJson(data))
        val sk = SlackClient()
        sk.doPost(data).also { println("Response: ${it.status}") }
    }
})
