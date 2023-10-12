import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    kotlin("jvm") version "1.8.21"
    kotlin("plugin.serialization") version "1.8.21"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    application
}
application {
    mainClass.set("sokos.ske.krav.MainKt")
}

group = "no.nav.sokos"

repositories {
    mavenCentral()
    maven { url = uri("https://maven.pkg.jetbrains.space/public/p/ktor/eap") }
}

val ktorVersion= "2.3.3"
val hikaricpVersion = "5.0.1"
val kotlinLoggingVersion = "3.0.4"
val logback_version = "1.4.1"
val logstash_version = "7.3"
val kotestVersion = "5.6.2"
val mockkVersion = "1.13.7"
val testContainerVersion ="1.19.0"
val mockFtpServerVersion = "3.1.0"
val nimbusVersion = "9.25.6"
val jacksonVersion = "2.14.0"
val gsonVersion = "2.10.1"
val natpryceVersion = "1.6.10.0"
val postgresqlVersion = "42.6.0"
val flyway_version = "9.16.1"


dependencies {
    // Ktor Server
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation ("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation ("io.ktor:ktor-server-status-pages:$ktorVersion")
    implementation ("io.ktor:ktor-server-call-id:$ktorVersion")
    implementation ("io.ktor:ktor-server-call-logging:$ktorVersion")

    // Ktor Client
    implementation ("io.ktor:ktor-client-apache:$ktorVersion")
    implementation ("io.ktor:ktor-client-auth:$ktorVersion")
    implementation ("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")

    // Security
    implementation ("io.ktor:ktor-server-auth-jwt:$ktorVersion")
    implementation ("com.nimbusds:nimbus-jose-jwt:$nimbusVersion")

    // Database
    implementation("com.zaxxer:HikariCP:$hikaricpVersion")
    implementation("org.postgresql:postgresql:$postgresqlVersion")
    implementation("no.nav:vault-jdbc:1.3.10")

    // Serialization
    implementation ("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation ("io.ktor:ktor-serialization-kotlinx-xml:$ktorVersion")
    implementation ("io.ktor:ktor-serialization-kotlinx-cbor:$ktorVersion")
    implementation ("io.ktor:ktor-serialization-kotlinx-protobuf:$ktorVersion")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.5.1")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.3.2")
    implementation ("io.ktor:ktor-serialization-jackson-jvm:$ktorVersion")
    implementation ("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
    implementation ("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
    implementation ("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")

    // FTP
    implementation ("commons-net:commons-net:3.9.0")
    implementation("com.github.mwiede:jsch:0.2.11")


    // Config
    implementation("com.natpryce:konfig:$natpryceVersion")

    // Flyway
    implementation("org.flywaydb:flyway-core:$flyway_version")

    // Monitorering
    implementation ("io.ktor:ktor-server-metrics-micrometer:$ktorVersion")

    // Logging
    implementation ("io.ktor:ktor-client-logging:$ktorVersion")
    implementation ("ch.qos.logback:logback-core:$logback_version")
    implementation ("ch.qos.logback:logback-classic:$logback_version")
    implementation ("net.logstash.logback:logstash-logback-encoder:$logstash_version")

    implementation ("io.github.microutils:kotlin-logging-jvm:$kotlinLoggingVersion")


    // Test
    testImplementation("io.kotest:kotest-assertions-core-jvm:$kotestVersion")
    testImplementation("io.kotest:kotest-runner-junit5:$kotestVersion")
    testImplementation("io.ktor:ktor-client-mock:$ktorVersion")
    testImplementation("io.mockk:mockk:$mockkVersion")
    testImplementation("org.testcontainers:postgresql:$testContainerVersion")
    implementation("org.mockftpserver:MockFtpServer:$mockFtpServerVersion")

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
            attributes["Main-Class"] = "sokos.ske.krav.MainKt"
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