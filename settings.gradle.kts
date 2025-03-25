pluginManagement {
    val kotlinVersion: String by settings
    val springBootVersion: String by settings

    plugins {
        id("org.springframework.boot") version springBootVersion
        id("io.spring.dependency-management") version "1.1.7"
        kotlin("jvm") version kotlinVersion
        kotlin("plugin.spring") version kotlinVersion
        kotlin("plugin.jpa") version kotlinVersion
        kotlin("plugin.allopen") version kotlinVersion
        kotlin("plugin.noarg") version kotlinVersion
    }

    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "meetjyou"
