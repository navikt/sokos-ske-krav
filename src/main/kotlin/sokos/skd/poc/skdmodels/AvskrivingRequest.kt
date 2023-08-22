package sokos.skd.poc.skdmodels


data class AvskrivingRequest (

    val kravidentifikatortype: String,
    val kravidentifikator: String
) {
    enum class Kravidentifikatortype(val value: kotlin.String){
        SKATTEETATENSKRAVIDENTIFIKATOR("SKATTEETATENS_KRAVIDENTIFIKATOR");
    }
}