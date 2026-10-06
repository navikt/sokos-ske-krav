package no.nav.sokos.ske.krav.config

import io.getunleash.DefaultUnleash
import io.getunleash.FakeUnleash
import io.getunleash.Unleash
import io.getunleash.util.UnleashConfig

private val logger = mu.KotlinLogging.logger {}

private const val TOBBLE_LES_FIL_SUFFIX = "les-fil.enabled"
private const val TOGGLE_SEND_KRAV_SUFFIX = "send-krav.enabled"
private const val TOGGLE_MOTTAKSSTATUS_SUFFIX = "mottaksstatus.enabled"

class UnleashConfig {
    private val unleashClient: Unleash
    private val appProperties = PropertiesConfig.applicationProperties
    private val unleashProps = PropertiesConfig.unleashProperties
    private val lastKnownStates = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

    private fun toggleName(suffix: String) = "${appProperties.appName}.$suffix"

    private fun isEnabled(suffix: String): Boolean {
        val name = toggleName(suffix)
        val enabled = unleashClient.isEnabled(name)
        val previous = lastKnownStates.put(name, enabled)

        if (previous != null && previous != enabled) {
            logger.info { "Feature toggle '$name' changed from $previous to $enabled" }
        }

        return enabled
    }

    fun isLesFilEnabled(): Boolean = isEnabled(TOBBLE_LES_FIL_SUFFIX)

    fun isSendKravEnabled(): Boolean = isEnabled(TOGGLE_SEND_KRAV_SUFFIX)

    fun isMottaksstatusEnabled(): Boolean = isEnabled(TOGGLE_MOTTAKSSTATUS_SUFFIX)

    init {
        if (appProperties.isLocal || appProperties.isTest) {
            unleashClient =
                FakeUnleash().also { fakeUnleash ->
                    fakeUnleash.enable(toggleName(TOGGLE_SEND_KRAV_SUFFIX))
                    fakeUnleash.enable(toggleName(TOGGLE_MOTTAKSSTATUS_SUFFIX))
                    fakeUnleash.enable(toggleName(TOBBLE_LES_FIL_SUFFIX))
                }
        } else {
            val config =
                UnleashConfig
                    .builder()
                    .appName(appProperties.appName)
                    .instanceId(appProperties.podName)
                    .unleashAPI(unleashProps.unleashAPI + "/api/")
                    .apiKey(unleashProps.apiKey)
                    .synchronousFetchOnInitialisation(true)
                    .build()
            unleashClient = DefaultUnleash(config)
        }
    }
}
