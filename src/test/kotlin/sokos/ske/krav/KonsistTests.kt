package sokos.ske.krav

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.architecture.KoArchitectureCreator.assertArchitecture
import com.lemonappdev.konsist.api.architecture.Layer
import com.lemonappdev.konsist.api.ext.list.withAllAnnotationsOf
import com.lemonappdev.konsist.api.ext.list.withName
import com.lemonappdev.konsist.api.ext.list.withNameContaining
import com.lemonappdev.konsist.api.ext.list.withNameEndingWith
import com.lemonappdev.konsist.api.verify.assertFalse
import com.lemonappdev.konsist.api.verify.assertTrue
import io.kotest.core.spec.style.FunSpec
import kotlinx.serialization.Serializable

internal class KonsistTests : FunSpec({

    test("Test layer boundary") {
        Konsist.scopeFromProduction()
            .assertArchitecture {
                val domain = Layer("Domain", "sokos.ske.krav.domain..")
                val config = Layer("Config", "sokos.ske.krav.config..")

                val database = Layer("Database", "sokos.ske.krav.database..")
                val security = Layer("Security", "sokos.ske.krav.security..")

                val client = Layer("Client", "sokos.ske.krav.client..")
                val service = Layer("Service", "sokos.ske.krav.service..")


                domain.dependsOnNothing()
                // TODO: uncomment når SKEApi er fjernet
                //config.dependsOnNothing()

                database.dependsOn(domain, config)
                security.dependsOn(domain, config)

                client.dependsOn(security, config)
                service.dependsOn(domain, config, client, database)
            }
    }

    test("Package test") {
        val koscope = Konsist.scopeFromProduction()

        koscope
            .classes()
            .withAllAnnotationsOf(Serializable::class)
            .assertTrue {
                it.resideInPackage("sokos.ske.krav.domain.ske..")
                    || it.resideInPackage("sokos.ske.krav.domain.maskinporten..")
                    || it.resideInPackage("sokos.ske.krav.config..")
            }

        koscope
            .classes()
            .withNameContaining("Service")
            .assertTrue { it.resideInPackage("sokos.ske.krav.service") }

        koscope
            .packages
            .withName("service")
            .assertTrue { it.containingFile.hasNameEndingWith("Service") }

        koscope
            .classes()
            .withNameContaining("Repository", "Table")
            .assertTrue { it.resideInPackage("sokos.ske.krav.database..") }

        koscope
            .classes()
            .withNameEndingWith("Request")
            .assertTrue { it.resideInPackage("sokos.ske.krav.domain.ske.requests") }

        koscope
            .packages
            .withName("request")
            .assertTrue { it.containingFile.hasNameEndingWith("Request") }
        koscope
            .classes()
            .withNameEndingWith("Response")
            .assertTrue { it.resideInPackage("sokos.ske.krav.domain.ske.responses") }

        koscope
            .packages
            .withName("response")
            .assertTrue { it.containingFile.hasNameEndingWith("Response") }

        koscope
            .classes()
            .withNameEndingWith("Config")
            .assertTrue { it.resideInPackage("sokos.ske.krav.config") }

        koscope
            .packages
            .withName("config")
            .assertTrue { it.containingFile.hasNameEndingWith("Config") }

    }

    test("Clean code test") {
        val koscope = Konsist.scopeFromProduction()

        koscope
            .interfaces()
            .assertTrue { it.hasFunModifier || it.hasPublicModifier }

        koscope
            .properties()
            .assertTrue { !it.hasPublicModifier && it.numModifiers < 5 }

        koscope
            .imports
            .assertFalse { it.isWildcard }

        koscope
            .functions()
            .assertTrue { it.numParameters < 5 }

    }
})
