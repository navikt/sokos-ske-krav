import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    kotlin("jvm") version "1.8.21"
    kotlin("plugin.serialization") version "1.8.21"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    application
}
application {
    mainClass.set("sokos.skd.poc.Mainkt")
}

group = "no.nav.sokos"

repositories {
    mavenCentral()
    maven { url = uri("https://maven.pkg.jetbrains.space/public/p/ktor/eap") }
}

val ktorVersion= "2.3.3"
val kotlinLoggingVersion = "3.0.4"
val kotestVersion = "5.6.2"
val mockkVersion = "1.13.4"
val nimbusVersion = "9.25.6"
val jacksonVersion = "2.14.0"
val gsonVersion = "2.10.1"

dependencies {
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation ("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation ("io.ktor:ktor-server-status-pages:$ktorVersion")
    implementation ("io.ktor:ktor-server-call-id:$ktorVersion")
    implementation ("io.ktor:ktor-server-call-logging:$ktorVersion")
    implementation ("io.ktor:ktor-server-metrics-micrometer:$ktorVersion")
    implementation ("io.ktor:ktor-server-auth-jwt:$ktorVersion")
    implementation ("io.ktor:ktor-client-apache:$ktorVersion")
    implementation ("io.ktor:ktor-client-auth:$ktorVersion")
    implementation ("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")

    implementation ("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation ("io.ktor:ktor-serialization-kotlinx-xml:$ktorVersion")
    implementation ("io.ktor:ktor-serialization-kotlinx-cbor:$ktorVersion")
    implementation ("io.ktor:ktor-serialization-kotlinx-protobuf:$ktorVersion")
    implementation ("io.ktor:ktor-serialization-jackson-jvm:$ktorVersion")
    implementation ("com.google.code.gson:gson:$gsonVersion")
    implementation ("commons-net:commons-net:3.9.0")

    implementation ("com.nimbusds:nimbus-jose-jwt:$nimbusVersion")
    implementation ("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
    implementation ("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
    implementation ("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")

    implementation ("io.ktor:ktor-client-logging:$ktorVersion")
    implementation ("io.github.microutils:kotlin-logging-jvm:$kotlinLoggingVersion")

    testImplementation("org.jetbrains.kotlin:kotlin-test")

}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}
sourceSets {
    main {
        java {
            srcDirs("$buildDir/generated/src/main/kotlin")
        }
    }
}


tasks {
    withType<ShadowJar>().configureEach {
        enabled = true
        archiveFileName.set("app.jar")
        manifest {
            attributes["Main-Class"] = "sokos.skd.poc.Mainkt"
        }
    }

    ("jar") {
        enabled = false
    }


    withType<Test>().configureEach {
        useJUnitPlatform()
        testLogging {
            exceptionFormat = TestExceptionFormat.FULL
            events("passed", "skipped", "failed")
        }

        reports.forEach { report -> report.required.value(false) }
    }
}