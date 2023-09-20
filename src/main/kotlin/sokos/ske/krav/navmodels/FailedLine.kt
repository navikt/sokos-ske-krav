package sokos.ske.krav.navmodels

import io.ktor.http.*

data class FailedLine(
    val detailLine: DetailLine,
    val httpStatusCode: HttpStatusCode,
    val responseBody: String
)
