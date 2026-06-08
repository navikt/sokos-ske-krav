import kotlinx.kover.gradle.plugin.dsl.tasks.KoverReport

import org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.3.21"
    kotlin("plugin.serialization") version "2.3.21"
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
    id("org.jetbrains.kotlinx.kover") version "0.9.8"

    application
}

group = "no.nav.sokos"

repositories {
    mavenCentral()

    maven { url = uri("https://github-package-registry-mirror.gc.nav.no/cached/maven-release") }
}

val ktorVersion = "3.4.3"
val jschVersion = "2.28.0"
val nimbusVersion = "10.9"
val kotlinxSerializationVersion = "1.11.0"
val kotlinxDatetimeVersion = "0.7.1-0.6.x-compat"

val vaultVersion = "1.3.10"
val prometheusVersion = "1.16.5"
val opentelemetryVersion = "2.27.0-alpha"

// DB
val hikaricpVersion = "7.0.2"
val flywayVersion = "12.5.0"
val postgresqlVersion = "42.7.11"
val kotliqueryVersion = "2.0.5"

// Test
val kotestVersion = "6.1.11"

val mockkVersion = "1.14.9"
val commonsVersion = "3.13.0"
val testContainerVersion = "1.21.4"
val sshdSftpVersion = "2.17.1"

// Logging
val janinoVersion = "3.1.12"
val kotlinLoggingVersion = "3.0.5"
val logbackVersion = "1.5.32"
val logstashVersion = "9.0"

val resilience4jVersion = "2.4.0"

dependencies {
    // Ktor Server
    implementation("io.ktor:ktor-server-call-logging-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-call-id-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-netty-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-html-builder:$ktorVersion")

    // Ktor Client
    implementation("io.ktor:ktor-client-content-negotiation-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-apache5-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-logging:$ktorVersion")

    // Security
    implementation("io.ktor:ktor-server-auth-jwt-jvm:$ktorVersion")
    implementation("com.nimbusds:nimbus-jose-jwt:$nimbusVersion")

    // Database
    implementation("com.zaxxer:HikariCP:$hikaricpVersion")
    implementation("org.postgresql:postgresql:$postgresqlVersion")
    implementation("no.nav:vault-jdbc:$vaultVersion")
    implementation("no.nav:kotliquery:$kotliqueryVersion")

    implementation("org.flywaydb:flyway-core:$flywayVersion")
    runtimeOnly("org.flywaydb:flyway-database-postgresql:$flywayVersion")

    // Serialization
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:$ktorVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:$kotlinxSerializationVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime-jvm:$kotlinxDatetimeVersion")

    // FTP
    implementation("com.github.mwiede:jsch:$jschVersion")

    // Opentelemetry
    implementation("io.opentelemetry.instrumentation:opentelemetry-ktor-3.0:$opentelemetryVersion")

    // metrics
    implementation("io.ktor:ktor-server-metrics-micrometer:$ktorVersion")
    implementation("io.micrometer:micrometer-registry-prometheus:$prometheusVersion")

    // Logging
    implementation("io.github.microutils:kotlin-logging-jvm:$kotlinLoggingVersion")
    runtimeOnly("org.codehaus.janino:janino:$janinoVersion")
    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("net.logstash.logback:logstash-logback-encoder:$logstashVersion")

    // Circuit Breaker
    implementation("io.github.resilience4j:resilience4j-circuitbreaker:$resilience4jVersion")
    implementation("io.github.resilience4j:resilience4j-kotlin:$resilience4jVersion")

    // Test
    testImplementation("io.kotest:kotest-assertions-core-jvm:$kotestVersion")
    testImplementation("io.kotest:kotest-runner-junit5-jvm:$kotestVersion")
    testImplementation("io.ktor:ktor-client-mock-jvm:$ktorVersion")
    testImplementation("io.mockk:mockk-jvm:$mockkVersion")
    testImplementation("org.testcontainers:postgresql:$testContainerVersion")
    testImplementation("commons-net:commons-net:$commonsVersion")
    testImplementation("org.apache.sshd:sshd-sftp:$sshdSftpVersion")
}

configurations.all {
    resolutionStrategy {
        eachDependency {
            // ./gradlew dependencies --configuration runtimeClasspath | grep logback-core

            // Critical
            if (requested.group == "org.lz4" && requested.name == "lz4-java") {
                useTarget("at.yawk.lz4:lz4-java:1.11.0")
                because("Prefer the patched fork for vulnerability fix")
            }
            // High
            if (requested.group == "com.fasterxml.jackson.core" && requested.name == "jackson-core") {
                useVersion("2.21.1")
                because("jackson-core: Number Length Constraint Bypass in Async Parser Leads to Potential DoS Condition. Affected version >= 2.19.0, < 2.21.1")
            }
            if (requested.group == "tools.jackson.core" && requested.name == "jackson-core") {
                useVersion("3.1.1")
                because("Jackson Core: Document length constraint bypass in blocking, async, and DataInput parsers. Affected version >= 3.0.0, <= 3.1.0")
            }
            if (requested.group == "org.eclipse.jetty" && requested.name == "jetty-server") {
                useVersion("9.4.57.v20241219")
                because("Eclipse Jetty's ThreadLimitHandler.getRemote() vulnerable to remote DoS attacks. Affected version >= 9.3.12, <= 9.4.55")
            }
            if (requested.group == "org.xerial.snappy" && requested.name == "snappy-java") {
                useVersion("1.1.10.4")
                because("snappy-java's missing upper bound check on chunk length can lead to Denial of Service (DoS) impact. Affected version <= 1.1.10.3")
            }
            if (requested.group == "io.netty" && requested.name == "netty-codec-http") {
                useVersion("4.2.13.Final")
                because("Netty HttpClientCodec response desynchronization (GHSA-57rv-r2g8-2cj3). Affected version = 4.2.11.Final, < 4.2.13.Final")
            }
            if (requested.group == "io.netty" && requested.name == "netty-codec-http2") {
                useVersion("4.2.13.Final")
                because("Netty HttpClientCodec response desynchronization (GHSA-57rv-r2g8-2cj3). Affected version = 4.2.11.Final, < 4.2.13.Final")
            }
            if (requested.group == "ch.qos.logback" && requested.name == "logback-core") {
                val parts = (requested.version ?: "").split(".")
                val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
                val patch = parts.getOrNull(2)?.split("-")?.firstOrNull()?.toIntOrNull() ?: 0
                if (minor < 5 || (minor == 5 && patch < 25)) {
                    useVersion("1.5.25")
                    because("CVE-2026-1225: ACE vulnerability <= 1.5.24; CVE-2024-12798: JaninoEventEvaluator ACE vulnerability <= 1.3.14")
                }
            }
            if (requested.group == "io.netty" && requested.name == "netty-transport-native-epoll") {
                useVersion("4.2.13.Final")
                because("Netty epoll transport denial of service via RST on half-closed TCP connection (GHSA-rwm7-x88c-3g2p). Affected version = 4.2.12.Final")
            }
        }
    }
}

// Test-only transitive dependency overrides
configurations.matching { it.name.startsWith("test") }.configureEach {
    resolutionStrategy {
        eachDependency {
            // Moderate
            if (requested.group == "org.apache.commons" && requested.name == "commons-compress") { // ./gradlew dependencies --configuration testRuntimeClasspath | grep commons-compress
                useVersion("1.26.0")
                because("Apache Commons Compress: OutOfMemoryError unpacking broken Pack200 file. Affected version >= 1.21, < 1.26.0")
            }
        }
    }
}

application {
    mainClass.set("no.nav.sokos.ske.krav.ApplicationKt")
}

sourceSets {
    main {
        java {
            srcDirs("${layout.buildDirectory.get()}/generated/src/main/kotlin")
        }
    }
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

tasks {
    withType<KotlinCompile>().configureEach {
        dependsOn("ktlintFormat")
    }

    withType<KoverReport>().configureEach {
        kover {
            reports {
                filters {
                    excludes {
                        // exclusion rules - classes to exclude from report
                        classes(
                            "sokos.ske.krav.api*",
                            "sokos.ske.krav.domain.maskinporten.*",
                            "sokos.ske.krav.security.*",
                            "sokos.ske.krav.config.*",
                            "*Application*",
                            "sokos.ske.krav.ApplicationState",
                            "sokos.ske.krav.database.PostgresDataSource",
                            "sokos.ske.krav.frontend.*",
                            "sokos.ske.krav.metrics.*",
                            "sokos.ske.krav.domain.slack",
                            "sokos.ske.krav.service.RapportService",
                            "sokos.ske.krav.service.RapportType",
                            "sokos.ske.krav.service.Directories",
                        )
                    }
                }
            }
        }
    }

    withType<Test>().configureEach {
        useJUnitPlatform()
        testLogging {
            showExceptions = true
            showStackTraces = true
            exceptionFormat = FULL
            events = setOf(TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED)
        }

        reports.forEach { report -> report.required.value(false) }

        finalizedBy(koverHtmlReport)
    }

    ("build") {
        dependsOn("copyPreCommitHook")
    }

    register<Copy>("copyPreCommitHook") {
        from(".scripts/pre-commit")
        into(".git/hooks")
        filePermissions {
            user {
                execute = true
            }
        }
        doFirst {
            println("Installing git hooks...")
        }
        doLast {
            println("Git hooks installed successfully.")
        }
        description = "Copy pre-commit hook to .git/hooks"
        group = "git hooks"
        outputs.upToDateWhen { false }
    }
}
