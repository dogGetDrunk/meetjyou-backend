package com.dogGetDrunk.meetjyou.loadtest;

import io.gatling.javaapi.core.ChainBuilder;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

// Focused abuse test for AuthRateLimitFilter (per-IP rate limit on auth endpoints).
// Goal: confirm the 5-6th request in a 1-minute window gets HTTP 429 as expected,
// then confirm whether spoofing X-Forwarded-For resets/bypasses the limit
// (server.forward-headers-strategy: native trusts XFF for remoteAddr).
//
// Run baseline (no spoofing):
//   ./gradlew gatlingRun --simulation com.dogGetDrunk.meetjyou.loadtest.AuthAbuseSimulation
// Run with spoofing enabled to test bypass:
//   ./gradlew gatlingRun --simulation com.dogGetDrunk.meetjyou.loadtest.AuthAbuseSimulation -DspoofXff=true
public class AuthAbuseSimulation extends Simulation {

    private static final String BASE_URL = System.getProperty("BASE_URL", "http://localhost:8080");
    private static final boolean SPOOF_XFF = Boolean.getBoolean("spoofXff");

    private static final HttpProtocolBuilder httpProtocol = http.baseUrl(BASE_URL)
            .contentTypeHeader("application/json");

    // A different fake client IP on every request, matching the k6 script's per-request spoofing.
    private static String randomSpoofedIp() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        return "10.0." + r.nextInt(255) + "." + r.nextInt(255);
    }

    private static final ChainBuilder sendNonceRequest = SPOOF_XFF
            ? exec(
                    http("POST /auth/nonce (spoofed XFF)")
                            .post("/api/v1/auth/nonce")
                            .header("X-Forwarded-For", session -> randomSpoofedIp())
                            .check(status().lt(500)))
            : exec(
                    http("POST /auth/nonce")
                            .post("/api/v1/auth/nonce")
                            .check(status().lt(500)));

    private static final ScenarioBuilder scn = scenario("Auth abuse - single client burst")
            .repeat(20).on(
                    sendNonceRequest,
                    pause(Duration.ofMillis(200)));

    {
        setUp(scn.injectOpen(atOnceUsers(1)))
                .protocols(httpProtocol)
                .maxDuration(Duration.ofMinutes(1));
    }
}
