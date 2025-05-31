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
    implementation("org.springframework.boot:spring-boot-starter-data-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.hibernate.common:hibernate-commons-annotations:7.0.3.Final")

    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

    implementation("com.oracle.oci.sdk:oci-java-sdk:3.38.0")
    implementation("com.oracle.oci.sdk:oci-java-sdk-common:3.38.0")
    implementation("com.oracle.oci.sdk:oci-java-sdk-objectstorage:3.38.0")
    implementation("com.oracle.oci.sdk:oci-java-sdk-common-httpclient:3.38.0")
    implementation("com.oracle.oci.sdk:oci-java-sdk-common-httpclient-choices:3.38.0")
    implementation("com.oracle.oci.sdk:oci-java-sdk-common-httpclient-jersey3:3.38.0")

    implementation("net.coobird:thumbnailator:0.4.20")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.7.0")


    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

//    runtimeOnly("com.mysql:mysql-connector-j")
    implementation("com.mysql:mysql-connector-j")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
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
