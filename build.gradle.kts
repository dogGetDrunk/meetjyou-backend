import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

val mysqlVersion: String by project

plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    kotlin("plugin.jpa")
    kotlin("plugin.allopen")
    kotlin("plugin.noarg")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("io.gatling.gradle") version "3.15.1.1"
}

group = "com.example"
version = "0.0.1-SNAPSHOT"

// The Gatling simulations under src/gatling are self-contained (java.net.http + Gatling DSL
// only) and don't reuse any main/test classes. Keeping the default includeMainOutput=true would
// pull io.spring.dependency-management's project-wide Netty version pin into the gatling
// configurations too, which conflicts with the Netty version Gatling itself needs
// (NoClassDefFoundError: io/netty/channel/IoOps) — see
// https://community.gatling.io/t/gatling-and-netty-version-conflicts/4246
gatling {
    includeMainOutput = false
    includeTestOutput = false
}

// includeMainOutput=false alone isn't enough: io.spring.dependency-management applies its BOM
// project-wide by default, so it still force-downgrades most io.netty artifacts in the gatling
// configurations to Spring Boot's managed version while leaving Netty-4.2-only artifacts (e.g.
// netty-transport-classes-io_uring, which Gatling itself pulls in) at the originally requested
// 4.2.x version. That mismatch is what throws NoClassDefFoundError: io/netty/channel/IoOps.
// Re-pin the gatling configuration's Netty artifacts back to the version Gatling actually
// requested; version filter excludes the unrelated 2.x-versioned netty-tcnative-* artifacts.
// resolutionStrategy must be applied directly to the resolvable configurations
// (gatlingCompileClasspath/gatlingRuntimeClasspath), not the declarative "gatling" one —
// extendsFrom does not propagate resolutionStrategy rules.
configurations.matching { it.name.startsWith("gatling") }.configureEach {
    resolutionStrategy.eachDependency {
        if (requested.group == "io.netty" && requested.version?.startsWith("4.") == true) {
            useVersion("4.2.14.Final")
        }
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.Embeddable")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("org.springframework.stereotype.Service")
    annotation("org.springframework.transaction.annotation.Transactional")
}

noArg {
    annotation("jakarta.persistence.Entity")
}

repositories {
    mavenCentral()
}

dependencies {
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    implementation("org.springframework.boot:spring-boot-starter-data-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")
    implementation("org.springframework.security:spring-security-oauth2-jose")
    implementation("org.hibernate.common:hibernate-commons-annotations:7.0.3.Final")

    // Cache library - Caffeine
    implementation("com.github.ben-manes.caffeine:caffeine:3.2.3")

    // Rate limiting
    implementation("com.bucket4j:bucket4j-core:8.10.1")

    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

    implementation("com.oracle.oci.sdk:oci-java-sdk-workrequests:3.38.0")
    implementation("com.oracle.oci.sdk:oci-java-sdk:3.38.0")
    implementation("com.oracle.oci.sdk:oci-java-sdk-common:3.38.0")
    implementation("com.oracle.oci.sdk:oci-java-sdk-objectstorage:3.38.0")
    implementation("com.oracle.oci.sdk:oci-java-sdk-common-httpclient:3.38.0")
    implementation("com.oracle.oci.sdk:oci-java-sdk-common-httpclient-choices:3.38.0")
    implementation("com.oracle.oci.sdk:oci-java-sdk-common-httpclient-jersey3:3.38.0")

    implementation("net.coobird:thumbnailator:0.4.20")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.7.0")

    // firebase admin sdk
    implementation("com.google.firebase:firebase-admin:9.5.0")

    // google api
    implementation("com.google.api-client:google-api-client:2.8.1")
    implementation("com.google.http-client:google-http-client-jackson2:2.0.2")
    implementation("com.google.code.gson:gson:2.13.2")

    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

//    runtimeOnly("com.mysql:mysql-connector-j")
    implementation("com.mysql:mysql-connector-j")

    // Flyway
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-mysql")

    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    // Kotest
    testImplementation(platform("io.kotest:kotest-bom:6.0.3"))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.kotest:kotest-runner-junit5:6.0.3")
    testImplementation("io.kotest:kotest-assertions-core:6.0.3")
    testImplementation("io.kotest:kotest-extensions-spring:6.0.3")

    // MockK
    testImplementation("io.mockk:mockk:1.14.5")

    testRuntimeOnly("com.h2database:h2")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.withType<KotlinJvmCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
        jvmTarget.set(JvmTarget.JVM_21)
    }
}
