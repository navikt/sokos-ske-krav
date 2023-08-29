package sokos.skd.poc.skdmodels

import kotlinx.serialization.Serializable

@Serializable
data class Skyldner (

    val identifikatortype: Identifikatortype,
    val identifikator: String
) {
    enum class Identifikatortype(val value: String){
        PERSON("PERSON"),
        ORGANISASJON("ORGANISASJON");
    }
}