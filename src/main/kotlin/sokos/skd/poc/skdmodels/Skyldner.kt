package sokos.skd.poc.skdmodels


data class Skyldner (

    val identifikatortype: Identifikatortype,
    val identifikator: kotlin.String
) {
    enum class Identifikatortype(val value: kotlin.String){
        PERSON("PERSON"),
        ORGANISASJON("ORGANISASJON");
    }
}