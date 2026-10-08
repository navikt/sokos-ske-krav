package no.nav.sokos.ske.krav.config

import io.getunleash.DefaultUnleash
import io.getunleash.FakeUnleash
import io.getunleash.Unleash
import io.getunleash.event.ClientFeaturesResponse
import io.getunleash.event.UnleashSubscriber
import io.getunleash.util.UnleashConfig

private val logger = mu.KotlinLogging.logger {}

private const val TOGGLE_LES_FIL_SUFFIX = "les-fil.enabled"
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

    fun isLesFilEnabled(): Boolean = unleashClient.isEnabled(toggleName(TOGGLE_LES_FIL_SUFFIX))

    fun isSendKravEnabled(): Boolean = unleashClient.isEnabled(toggleName(TOGGLE_SEND_KRAV_SUFFIX))

    fun isMottaksstatusEnabled(): Boolean = unleashClient.isEnabled(toggleName(TOGGLE_MOTTAKSSTATUS_SUFFIX))

    private val subscriber =
        object : UnleashSubscriber {
            override fun togglesFetched(toggleResponse: ClientFeaturesResponse) {
                val trackedNames =
                    setOf(
                        toggleName(TOGGLE_LES_FIL_SUFFIX),
                        toggleName(TOGGLE_SEND_KRAV_SUFFIX),
                        toggleName(TOGGLE_MOTTAKSSTATUS_SUFFIX),
                    )

                toggleResponse.features
                    .filter { it.name in trackedNames }
                    .forEach { toggle ->
                        val enabled = toggle.environmentEnabled()
                        val previous = lastKnownStates.put(toggle.name, enabled)

                        if (previous != null && previous != enabled) {
                            logger.info {
                                "Feature toggle '${toggle.name}' changed from $previous to $enabled"
                            }
                        }
                    }
            }
        }

    init {
        if (appProperties.isLocal || appProperties.isTest) {
            unleashClient =
                FakeUnleash().also { fakeUnleash ->
                    fakeUnleash.enable(toggleName(TOGGLE_SEND_KRAV_SUFFIX))
                    fakeUnleash.enable(toggleName(TOGGLE_MOTTAKSSTATUS_SUFFIX))
                    fakeUnleash.enable(toggleName(TOGGLE_LES_FIL_SUFFIX))
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
                    .subscriber(subscriber)
                    .build()
            unleashClient = DefaultUnleash(config)
        }
    }
}
