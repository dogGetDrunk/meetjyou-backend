package com.dogGetDrunk.meetjyou.config

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.string.shouldStartWith
import org.yaml.snakeyaml.Yaml
import java.io.File

/**
 * The actuator management chain trusts network isolation as its first layer: docker-compose.yml
 * (uploaded to the host as-is by deploy.yml) must publish the management port on loopback only.
 * Binding it to 0.0.0.0 would expose health/metrics/prometheus to the internet without auth.
 */
class ManagementPortBindingTest : BehaviorSpec({

    Given("docker-compose.yml and the configured management port") {
        val managementPort = readManagementPort()
        val mappings = readPortMappings().filter { it.substringAfterLast(":") == managementPort }

        Then("the management port is published") {
            mappings.shouldNotBeEmpty()
        }

        Then("every mapping of it binds to loopback only") {
            mappings.forEach { it shouldStartWith LOOPBACK_PREFIX }
        }
    }
})

private const val LOOPBACK_PREFIX = "127.0.0.1:"
private const val COMPOSE_FILE = "docker-compose.yml"
private const val APPLICATION_YML = "src/main/resources/application.yml"

@Suppress("UNCHECKED_CAST")
private fun readManagementPort(): String {
    val root = Yaml().load<Map<String, Any>>(File(APPLICATION_YML).readText())
    val management = root["management"] as Map<String, Any>
    val server = management["server"] as Map<String, Any>
    return server["port"].toString()
}

@Suppress("UNCHECKED_CAST")
private fun readPortMappings(): List<String> {
    val root = Yaml().load<Map<String, Any>>(File(COMPOSE_FILE).readText())
    val services = root["services"] as Map<String, Map<String, Any>>
    return services.values.flatMap { service ->
        (service["ports"] as? List<Any>).orEmpty().map { it.toString() }
    }
}
