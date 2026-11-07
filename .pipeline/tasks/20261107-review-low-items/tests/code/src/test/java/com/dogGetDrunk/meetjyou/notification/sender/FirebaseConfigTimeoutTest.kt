package com.dogGetDrunk.meetjyou.notification.sender

import com.google.auth.oauth2.AccessToken
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

/**
 * AT-004-1: FCM options carry explicit 10s connect/read/write timeouts (SDK default 0 = infinite).
 */
class FirebaseConfigTimeoutTest : BehaviorSpec({

    Given("AT-004-1 credentials built without any network access") {
        val credentials = GoogleCredentials.create(AccessToken("test", null))
        val appsBefore = FirebaseApp.getApps().size

        When("FirebaseConfig builds its options from them") {
            val options = FirebaseConfig().buildFirebaseOptions(credentials)

            Then("connect timeout is 10000 ms") {
                options.connectTimeout shouldBe EXPECTED_TIMEOUT_MS
            }

            Then("read timeout is 10000 ms") {
                options.readTimeout shouldBe EXPECTED_TIMEOUT_MS
            }

            Then("write timeout is 10000 ms") {
                options.writeTimeout shouldBe EXPECTED_TIMEOUT_MS
            }

            Then("no FirebaseApp is initialized") {
                FirebaseApp.getApps().size shouldBe appsBefore
            }
        }
    }
}) {
    private companion object {
        const val EXPECTED_TIMEOUT_MS = 10_000
    }
}
