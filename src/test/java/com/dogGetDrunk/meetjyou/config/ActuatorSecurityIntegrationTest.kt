package com.dogGetDrunk.meetjyou.config

import com.google.firebase.FirebaseApp
import com.oracle.bmc.auth.AuthenticationDetailsProvider
import com.oracle.bmc.objectstorage.ObjectStorageClient
import com.oracle.bmc.workrequests.WorkRequestClient
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles

/**
 * env and beans are exposed on purpose here: they stand in for an endpoint someone adds to
 * management.endpoints.web.exposure.include later, and must still be denied by the security chain.
 */
@SpringBootTest(
    webEnvironment = RANDOM_PORT,
    properties = [
        "management.server.port=0",
        "management.endpoints.web.exposure.include=health,metrics,prometheus,env,beans",
    ],
)
@ActiveProfiles("test")
// Spring Boot tests disable metrics exporters by default; without this the prometheus endpoint does not exist.
@AutoConfigureObservability(tracing = false)
@MockBean(
    FirebaseApp::class,
    AuthenticationDetailsProvider::class,
    ObjectStorageClient::class,
    WorkRequestClient::class,
)
class ActuatorSecurityIntegrationTest : BehaviorSpec() {

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @Value("\${local.management.port}")
    private var managementPort: Int = 0

    private fun get(path: String) =
        restTemplate.getForEntity("http://localhost:$managementPort/actuator/$path", String::class.java)

    init {
        extension(SpringExtension())

        Given("an unauthenticated request to the management port") {
            When("it calls the health endpoint") {
                val response = get("health")

                Then("it is allowed and hides component details") {
                    response.statusCode shouldBe HttpStatus.OK
                    response.body.orEmpty() shouldContain "\"status\""
                    response.body.orEmpty() shouldNotContain "components"
                }
            }

            When("it calls the metrics endpoint") {
                Then("it is allowed") {
                    get("metrics").statusCode shouldBe HttpStatus.OK
                }
            }

            When("it calls the prometheus endpoint") {
                val response = get("prometheus")

                Then("it is allowed and returns scrape output") {
                    response.statusCode shouldBe HttpStatus.OK
                    response.body.orEmpty() shouldContain "jvm_"
                }
            }

            When("it calls an exposed endpoint outside the allowlist") {
                Then("env is denied") {
                    get("env").statusCode shouldBeIn DENIED
                }
                Then("beans is denied") {
                    get("beans").statusCode shouldBeIn DENIED
                }
            }
        }
    }
}

// The test profile configures spring.security.user, so a denied anonymous request gets the basic-auth 401.
private val DENIED = listOf(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN)
