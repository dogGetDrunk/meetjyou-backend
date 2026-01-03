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
}

group = "com.example"
version = "0.0.1-SNAPSHOT"

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
    implementation("org.springframework.security:spring-security-oauth2-jose")
    implementation("org.hibernate.common:hibernate-commons-annotations:7.0.3.Final")

    // Cache library - Caffeine
    implementation("com.github.ben-manes.caffeine:caffeine:3.2.3")

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
