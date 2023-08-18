package sokos.skd.poc.maskinporten

import com.fasterxml.jackson.annotation.JsonAlias
import java.time.Instant

data class Token(
    @JsonAlias("access_token")
    val accessToken: String,
    @JsonAlias("expires_in")
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
