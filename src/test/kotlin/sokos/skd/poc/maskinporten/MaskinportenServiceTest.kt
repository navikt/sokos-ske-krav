package sokos.skd.poc.maskinporten

import org.junit.jupiter.api.Test

class MaskinportenServiceTest {

    private lateinit var maskinportenService: MaskinportenService
    private lateinit var stubUrl: String
    private lateinit var maskinportenConfig: Config.MaskinportenConfig

    private val configuration = Config.Configuration()


    @Test
    fun getToken() {

        maskinportenConfig = Config.MaskinportenConfig()
        maskinportenService = MaskinportenService(maskinportenConfig)

        println(System.getenv("NAIS_CLUSTER_NAME"))
        println("cliId: ${maskinportenConfig.clientId}")
        println("wellKnown: ${maskinportenConfig.wellKnownUrl}")
        println("key: ${maskinportenConfig.clientJwk.keyID}")
        println("Scope: $SCOPES")

        //val token = runBlocking { maskinportenService.getToken() }

        //println(token)
    }
}