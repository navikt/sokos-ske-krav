import kotlinx.kover.gradle.plugin.dsl.tasks.KoverReport

import org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.3.10"
    kotlin("plugin.serialization") version "2.3.10"
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
    id("org.jetbrains.kotlinx.kover") version "0.9.8"

    application
}

group = "no.nav.sokos"

repositories {
    mavenCentral()
}

val ktorVersion = "3.4.2"
val jschVersion = "2.27.9"
val nimbusVersion = "10.8"
val kotlinxSerializationVersion = "1.10.0"
val kotlinxDatetimeVersion = "0.7.1-0.6.x-compat"

val vaultVersion = "1.3.10"
val prometheusVersion = "1.16.4"
val opentelemetryVersion = "2.26.1-alpha"

// DB
val hikaricpVersion = "7.0.2"
val flywayVersion = "12.2.0"
val postgresqlVersion = "42.7.10"
val kotliqueryVersion = "1.9.1"

// Test
val kotestVersion = "6.1.10"

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
    implementation("com.github.seratch:kotliquery:$kotliqueryVersion")

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
                useTarget("at.yawk.lz4:lz4-java:1.10.4")
                because("Prefer the patched fork for vulnerability fix")
            }
            // High
            if (requested.group == "com.fasterxml.jackson.core" && requested.name == "jackson-core") {
                useVersion("2.21.1")
                because("jackson-core: Number Length Constraint Bypass in Async Parser Leads to Potential DoS Condition. Affected version >= 2.19.0, < 2.21.1")
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
                useVersion("4.2.11.Final")
                because("Netty: HTTP Request Smuggling via Chunked Extension Quoted-String Parsing. Affected version >= 4.2.0.Alpha1, < 4.2.10.Final")
            }
            if (requested.group == "io.netty" && requested.name == "netty-codec-http2") {
                useVersion("4.2.11.Final")
                because("Netty HTTP/2 CONTINUATION Frame Flood DoS via Zero-Byte Frame Bypass. Affected version >= 4.2.0.Alpha1, < 4.2.10.Final")
            }

            // Moderate
            // Test
            if (requested.group == "org.apache.commons " && requested.name == "commons-compress") { // ./gradlew dependencies --configuration testRuntimeClasspath | grep commons-compress
                useVersion("1.26.0")
                because("Apache Commons Compress: OutOfMemoryError unpacking broken Pack200 filet. Affected version >= 1.21, < 1.26.0")
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
