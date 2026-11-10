package com.dogGetDrunk.meetjyou.support

import org.springframework.beans.factory.config.YamlPropertiesFactoryBean
import org.springframework.core.io.ClassPathResource
import java.util.Properties

/**
 * The Connector/J properties the app sets in application.yml, read from the real file so tests that
 * connect with them fail when a property is removed there.
 */
object ApplicationDriverProperties {
    private const val APPLICATION_YAML = "application.yml"
    private const val DRIVER_PROPERTY_PREFIX = "spring.datasource.hikari.data-source-properties."

    fun load(): Map<String, String> {
        val yaml = YamlPropertiesFactoryBean()
        yaml.setResources(ClassPathResource(APPLICATION_YAML))
        val properties = yaml.getObject() ?: return emptyMap()
        // Values keep their YAML types (Boolean), so stringPropertyNames() would skip them.
        return properties.keys.map { it.toString() }
            .filter { it.startsWith(DRIVER_PROPERTY_PREFIX) }
            .associate { it.removePrefix(DRIVER_PROPERTY_PREFIX) to properties[it].toString() }
    }

    fun asProperties(): Properties {
        val properties = Properties()
        load().forEach { (key, value) -> properties.setProperty(key, value) }
        return properties
    }
}
