package sokos.skd.poc.navmodels

import kotlinx.serialization.json.JsonElement

data class FtpFil(
    val name:String,
    val content: List<String>,
    val skeRequest: JsonElement
) {
}