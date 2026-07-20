package com.dogGetDrunk.meetjyou.loadtest;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

// Read-focused benchmark for the real OCI production instance.
// Purpose: measure GET /api/v1/posts latency before/after the N+1 fix, using the
// dedicated synthetic load-test-token account. Kept read-only and modest concurrency
// out of respect for OCI free-tier resources (HikariCP pool size 5, shared compute).
//
// Run: ./gradlew gatlingRun --simulation com.dogGetDrunk.meetjyou.loadtest.ProdPostsReadSimulation -DSECRET=<LOAD_TEST_TOKEN_SECRET>
public class ProdPostsReadSimulation extends Simulation {

    private static final String BASE_URL = System.getProperty("BASE_URL", "https://meetjyou.duckdns.org");
    private static final String SECRET = System.getProperty("SECRET");

    private static final Pattern ACCESS_TOKEN_PATTERN = Pattern.compile("\"accessToken\"\\s*:\\s*\"([^\"]+)\"");

    private static final HttpProtocolBuilder httpProtocol = http.baseUrl(BASE_URL);

    // Fetch the token once, outside the load-generation engine, and share it across all
    // injected users — the token endpoint is intentionally rate-limited (5/min), so per-user
    // fetching would exhaust it immediately.
    private static String accessToken;

    @Override
    public void before() {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/api/v1/internal/load-test-token"))
                    .header("X-Load-Test-Secret", SECRET)
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException(
                        "Failed to obtain load-test token: " + response.statusCode() + " " + response.body());
            }
            Matcher matcher = ACCESS_TOKEN_PATTERN.matcher(response.body());
            if (!matcher.find()) {
                throw new IllegalStateException("accessToken missing from load-test-token response");
            }
            accessToken = matcher.group(1);
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException("Failed to obtain load-test token", e);
        }
    }

    private static final ScenarioBuilder scn = scenario("Prod posts read")
            .during(Duration.ofSeconds(140)).on(
                    exec(http("GET /posts").get("/api/v1/posts?page=0&size=10")
                            .header("Authorization", session -> "Bearer " + accessToken)
                            .check(status().is(200))),
                    pause(Duration.ofSeconds(1), Duration.ofSeconds(2)));

    {
        setUp(scn.injectClosed(
                rampConcurrentUsers(0).to(10).during(Duration.ofSeconds(20)),
                constantConcurrentUsers(10).during(Duration.ofSeconds(40)),
                rampConcurrentUsers(10).to(30).during(Duration.ofSeconds(20)),
                constantConcurrentUsers(30).during(Duration.ofSeconds(40)),
                rampConcurrentUsers(30).to(0).during(Duration.ofSeconds(20))))
                .protocols(httpProtocol)
                .assertions(global().failedRequests().percent().lt(5.0));
    }
}
