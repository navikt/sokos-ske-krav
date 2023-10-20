package sokos.ske.krav.maskinporten

import kotlinx.serialization.SerialName
import java.time.Instant

data class Token(
    @SerialName("access_token")
    val accessToken: String,
    @SerialName("expires_in")
    val expiresIn: Long
)
 data class AccessToken(
    val accessToken: String,
    val expiresAt: Instant
) {
    constructor(token: Token) : this(
        accessToken = token.accessToken,
        expiresAt = Instant.now().plusSeconds(token.expiresIn)
    )
}
