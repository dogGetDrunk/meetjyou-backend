package com.dogGetDrunk.meetjyou.loadtest;

import io.gatling.javaapi.core.ChainBuilder;
import io.gatling.javaapi.core.ClosedInjectionStep;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

// Full READ-ONLY sweep of the real production instance (meetjyou.duckdns.org).
// Covers every GET endpoint reachable by a normal authenticated user, plus the permitAll
// public GETs — replaces ProdPostsReadSimulation (which only covered GET /posts) as the "full
// API" read-only baseline for production.
//
// Auth: uses the synthetic load-test account via POST /internal/load-test-token
// (LoadTestTokenController — gated by X-Load-Test-Secret, must be enabled + configured in
// production secrets). This avoids needing a real Kakao/Google OAuth login in the release
// profile. The synthetic account has no pre-existing posts/plans/parties/chat rooms unless a
// prior run created them, so UUID-scoped endpoints are exercised opportunistically (UUIDs
// discovered from list responses) and checks accept 403/404 as legitimate non-error outcomes,
// not just 200.
//
// Deliberately EXCLUDED (this is a read-only pass by explicit decision, not full coverage):
// - Every write endpoint (POST/PUT/PATCH/DELETE) — avoids polluting production data or
//   triggering real push notifications / OCI writes.
// - All OracleObjectStorageController PAR endpoints and GET /terms/{uuid}/content-url — these
//   generate real OCI PAR URLs even on a GET, so out of scope (same exclusion as the local
//   FullApiScenarioSimulation).
// - Every ADMIN-only GET: GET /users, GET /parties, GET /parties/user/{uuid}, GET
//   /parties/plan/{uuid}, GET /parties/{uuid} (admin single lookup), GET /plans/user/{uuid},
//   GET /{platform}/versions (list-all). Reaching these would require promoting the synthetic
//   account to ADMIN in production — a real, hard-to-reverse privilege change on a live
//   environment — so it is not done here.
// - Chat message send (STOMP, not REST — see .claude/rules/chat.md).
//
// Capacity sweep: injection profile below doubles VUs each stage (5 -> 10 -> 20 -> 40 -> 70)
// against the real OCI Always Free boxes (AMD E2.1.Micro, 1/8 OCPU / 1GB each, app and MySQL
// on separate instances). The goal is finding where this hardware actually breaks — not
// hitting a pre-picked target RPS — so the global assertions below are expected to start
// failing at one of the higher stages by design; that failure point IS the benchmark result.
// before() prints a wall-clock timestamp table for the stage boundaries so `vmstat`/OCI console
// metrics captured on both boxes during the run can be lined up against a specific stage. The
// synthetic token is refreshed on a background schedule (see TOKEN_REFRESH_INTERVAL) because the
// sweep's total run time exceeds the token's TTL — see fetchToken()/before() for why.
//
// Run: ./gradlew gatlingRun --simulation com.dogGetDrunk.meetjyou.loadtest.ProdFullReadScenarioSimulation -DSECRET=<LOAD_TEST_TOKEN_SECRET>
public class ProdFullReadScenarioSimulation extends Simulation {

    private static final String BASE_URL = System.getProperty("BASE_URL", "https://meetjyou.duckdns.org");
    private static final String SECRET = System.getProperty("SECRET");

    private static final Pattern ACCESS_TOKEN_PATTERN = Pattern.compile("\"accessToken\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern USER_UUID_CLAIM_PATTERN = Pattern.compile("\"userUuid\"\\s*:\\s*\"([^\"]+)\"");

    private static final HttpProtocolBuilder httpProtocol = http.baseUrl(BASE_URL);

    private static String accessToken;
    private static String syntheticUserUuid;

    // Each stage ramps from the previous target up to targetVUs, then holds there.
    // holdDuration is deliberately long (30s) relative to rampDuration (10s) so vmstat/OCI
    // console readings taken a few seconds apart both land inside the "settled" window.
    private record Stage(int targetVUs, Duration rampDuration, Duration holdDuration) {
    }

    private static final List<Stage> CAPACITY_SWEEP_STAGES = List.of(
            new Stage(5, Duration.ofSeconds(10), Duration.ofSeconds(30)),
            new Stage(10, Duration.ofSeconds(10), Duration.ofSeconds(30)),
            new Stage(20, Duration.ofSeconds(10), Duration.ofSeconds(30)),
            new Stage(40, Duration.ofSeconds(10), Duration.ofSeconds(30)),
            new Stage(70, Duration.ofSeconds(10), Duration.ofSeconds(30)));

    private static final Duration RAMP_DOWN_DURATION = Duration.ofSeconds(10);

    private static ClosedInjectionStep[] buildCapacitySweepProfile() {
        List<ClosedInjectionStep> steps = new ArrayList<>();
        int previousVUs = 0;
        for (Stage stage : CAPACITY_SWEEP_STAGES) {
            steps.add(rampConcurrentUsers(previousVUs).to(stage.targetVUs()).during(stage.rampDuration()));
            steps.add(constantConcurrentUsers(stage.targetVUs()).during(stage.holdDuration()));
            previousVUs = stage.targetVUs();
        }
        steps.add(rampConcurrentUsers(previousVUs).to(0).during(RAMP_DOWN_DURATION));
        return steps.toArray(new ClosedInjectionStep[0]);
    }

    // Prints when each stage is expected to start, in both elapsed-seconds and wall-clock form,
    // so a human watching `vmstat`/OCI console metrics on the app and DB boxes can tell which
    // stage a given reading belongs to without doing the arithmetic by hand mid-run.
    private static void logCapacitySweepSchedule(Instant startedAt) {
        DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_TIME;
        System.out.println("capacity sweep schedule (start=" + formatter.format(startedAt.atZone(java.time.ZoneId.systemDefault())) + "):");
        long elapsedSeconds = 0;
        int previousVUs = 0;
        for (Stage stage : CAPACITY_SWEEP_STAGES) {
            long rampStart = elapsedSeconds;
            elapsedSeconds += stage.rampDuration().toSeconds();
            long holdStart = elapsedSeconds;
            elapsedSeconds += stage.holdDuration().toSeconds();
            System.out.println("  t+" + rampStart + "s (" + formatter.format(startedAt.plusSeconds(rampStart).atZone(java.time.ZoneId.systemDefault()))
                    + "): ramp " + previousVUs + " -> " + stage.targetVUs() + " VUs over " + stage.rampDuration().toSeconds() + "s");
            System.out.println("  t+" + holdStart + "s (" + formatter.format(startedAt.plusSeconds(holdStart).atZone(java.time.ZoneId.systemDefault()))
                    + "): hold at " + stage.targetVUs() + " VUs until t+" + elapsedSeconds + "s");
            previousVUs = stage.targetVUs();
        }
        System.out.println("  t+" + elapsedSeconds + "s: ramp " + previousVUs + " -> 0 VUs over " + RAMP_DOWN_DURATION.toSeconds() + "s");
    }

    // LoadTestTokenResponse only carries {accessToken, expiresAt} — no uuid field. The synthetic
    // account's uuid lives in the JWT's own "userUuid" claim (JwtProvider.generateAccessToken),
    // not the subject (which is the email), so it has to be decoded out of the token itself.
    private static String decodeJwtUuid(String jwt) {
        String[] parts = jwt.split("\\.");
        String payload = parts[1];
        int padding = (4 - payload.length() % 4) % 4;
        payload = payload + "=".repeat(padding);
        String claimsJson = new String(Base64.getUrlDecoder().decode(payload), StandardCharsets.UTF_8);
        Matcher matcher = USER_UUID_CLAIM_PATTERN.matcher(claimsJson);
        if (!matcher.find()) {
            return null;
        }
        return matcher.group(1);
    }

    // The load-test-token TTL defaults to 300s (LoadTestTokenProperties.ttlMillis) and the
    // capacity sweep's total wall-clock time (injection profile + each VU's trailing 140s
    // session) comfortably exceeds that. Fetching the token once in before() and holding it for
    // the whole run — as this simulation originally did — means everything after ~300s starts
    // failing with 401 EXPIRED_TOKEN, which shows up as a wall of "capacity" failures that are
    // actually just an expired credential, not a server-side error. Refreshing well inside the
    // TTL keeps failures meaningful: a KO past this point reflects the server, not the harness.
    private static final Duration TOKEN_REFRESH_INTERVAL = Duration.ofSeconds(120);

    private static ScheduledExecutorService tokenRefreshExecutor;

    private static void fetchToken() throws IOException, InterruptedException {
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
    }

    // Fetch the synthetic-account token once, outside the load-generation engine, and share it
    // across all injected users — the token endpoint issues a fixed-identity JWT and there is no
    // reason for every user to mint its own. authHeader() re-reads the `accessToken` field on
    // every request build, so a background refresh below is picked up without any Gatling-side
    // session plumbing.
    @Override
    public void before() {
        try {
            fetchToken();
            syntheticUserUuid = decodeJwtUuid(accessToken);
            if (syntheticUserUuid == null) {
                throw new IllegalStateException(
                        "Failed to decode userUuid claim from load-test token — aborting to avoid null-path requests.");
            }
            logCapacitySweepSchedule(Instant.now());
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException("Failed to obtain load-test token", e);
        }

        tokenRefreshExecutor = Executors.newSingleThreadScheduledExecutor();
        tokenRefreshExecutor.scheduleAtFixedRate(() -> {
            try {
                fetchToken();
            } catch (IOException | InterruptedException e) {
                System.out.println("token refresh failed, keeping previous token: " + e.getMessage());
            }
        }, TOKEN_REFRESH_INTERVAL.toSeconds(), TOKEN_REFRESH_INTERVAL.toSeconds(), TimeUnit.SECONDS);
    }

    @Override
    public void after() {
        if (tokenRefreshExecutor != null) {
            tokenRefreshExecutor.shutdownNow();
        }
    }

    private static String authHeader(io.gatling.javaapi.core.Session session) {
        return "Bearer " + accessToken;
    }

    private static ChainBuilder shortPause() {
        return pause(Duration.ZERO, Duration.ofMillis(500));
    }

    private static final ChainBuilder publicNoAuth = group("public (no auth)").on(
            exec(http("GET /notices").get("/api/v1/notices?page=0&size=20")
                    .check(status().is(200))
                    .check(jsonPath("$.content[0].uuid").optional().saveAs("noticeUuid"))),
            doIf(session -> session.contains("noticeUuid")).then(
                    exec(http("GET /notices/{uuid}").get("/api/v1/notices/#{noticeUuid}")
                            .check(status().is(200)))),
            exec(http("GET /terms/active").get("/api/v1/terms/active").check(status().is(200))),
            exec(http("GET /android/versions/check").get("/api/v1/android/versions/check?clientVersion=1.0.0")
                    .check(status().in(200, 400))),
            exec(http("GET /android/versions/latest").get("/api/v1/android/versions/latest")
                    .check(status().in(200, 404))),
            exec(http("GET /users/is-duplicate-nickname").get(session ->
                            "/api/v1/users/is-duplicate-nickname?nickname=gatlingprobe"
                                    + ThreadLocalRandom.current().nextInt(1_000_000))
                    .check(status().is(200))));

    private static final ChainBuilder userProfileAuth = group("user profile (auth)").on(
            exec(http("GET /users/me/profile").get("/api/v1/users/me/profile")
                    .header("Authorization", ProdFullReadScenarioSimulation::authHeader)
                    // 404 PREFERENCE_NOT_FOUND is expected here: the synthetic account never
                    // goes through PUT /users/me, so it has no gender/age/etc. preference rows.
                    .check(status().in(200, 404))),
            exec(http("GET /users/{uuid}/basic-info")
                    .get(session -> "/api/v1/users/" + syntheticUserUuid + "/basic-info")
                    .header("Authorization", ProdFullReadScenarioSimulation::authHeader)
                    .check(status().in(200, 404))),
            exec(http("GET /users/{uuid}/advanced-info")
                    .get(session -> "/api/v1/users/" + syntheticUserUuid + "/advanced-info")
                    .header("Authorization", ProdFullReadScenarioSimulation::authHeader)
                    .check(status().in(200, 404))),
            exec(http("GET /users/me/plans").get("/api/v1/users/me/plans")
                    .header("Authorization", ProdFullReadScenarioSimulation::authHeader)
                    .check(status().is(200))),
            exec(http("GET /users/me/parties").get("/api/v1/users/me/parties")
                    .header("Authorization", ProdFullReadScenarioSimulation::authHeader)
                    .check(status().is(200))),
            exec(http("GET /users/me/applications").get("/api/v1/users/me/applications")
                    .header("Authorization", ProdFullReadScenarioSimulation::authHeader)
                    .check(status().is(200))),
            exec(http("GET /users/me/posts").get("/api/v1/users/me/posts")
                    .header("Authorization", ProdFullReadScenarioSimulation::authHeader)
                    .check(status().is(200))),
            exec(http("GET /users/me/notification-settings").get("/api/v1/users/me/notification-settings")
                    .header("Authorization", ProdFullReadScenarioSimulation::authHeader)
                    .check(status().is(200))));

    private static final ChainBuilder postsAuth = group("posts (auth)").on(
            exec(http("GET /posts").get("/api/v1/posts?page=0&size=20")
                    .header("Authorization", ProdFullReadScenarioSimulation::authHeader)
                    .check(status().is(200))
                    .check(jsonPath("$.content[0].uuid").optional().saveAs("samplePostUuid"))
                    .check(jsonPath("$.content[0].planUuid").optional().saveAs("samplePlanUuid"))
                    .check(jsonPath("$.content[0].partyUuid").optional().saveAs("samplePartyUuid"))),
            exec(http("GET /posts/author/{uuid}")
                    .get(session -> "/api/v1/posts/author/" + syntheticUserUuid)
                    .header("Authorization", ProdFullReadScenarioSimulation::authHeader)
                    .check(status().is(200))),
            doIf(session -> session.contains("samplePostUuid")).then(
                    exec(http("GET /posts/{uuid}").get("/api/v1/posts/#{samplePostUuid}")
                            .header("Authorization", ProdFullReadScenarioSimulation::authHeader)
                            .check(status().is(200)))));

    // Only exercised when a listed post happens to carry a public planUuid — the synthetic
    // account owns no plans of its own, and there is no plan-listing endpoint to discover one.
    private static final ChainBuilder planAuthBestEffort = group("plan (auth, best-effort)").on(
            doIf(session -> session.contains("samplePlanUuid")).then(
                    exec(http("GET /plans/{uuid}").get("/api/v1/plans/#{samplePlanUuid}")
                            .header("Authorization", ProdFullReadScenarioSimulation::authHeader)
                            .check(status().in(200, 403, 404))),
                    exec(http("GET /plans/{uuid}/markers").get("/api/v1/plans/#{samplePlanUuid}/markers")
                            .header("Authorization", ProdFullReadScenarioSimulation::authHeader)
                            .check(status().in(200, 403, 404)))));

    private static final ChainBuilder partyAuthBestEffort = group("party (auth, best-effort)").on(
            doIf(session -> session.contains("samplePartyUuid")).then(
                    exec(http("GET /parties/{uuid}/members").get("/api/v1/parties/#{samplePartyUuid}/members")
                            .header("Authorization", ProdFullReadScenarioSimulation::authHeader)
                            .check(status().in(200, 403, 404))),
                    exec(http("GET /parties/{uuid}/join-requests").get("/api/v1/parties/#{samplePartyUuid}/join-requests")
                            .header("Authorization", ProdFullReadScenarioSimulation::authHeader)
                            .check(status().in(200, 403, 404)))));

    private static final ChainBuilder chatAuth = group("chat (auth)").on(
            exec(http("GET /chat/rooms").get("/api/v1/chat/rooms")
                    .header("Authorization", ProdFullReadScenarioSimulation::authHeader)
                    .check(status().is(200))
                    .check(jsonPath("$.rooms[0].roomUuid").optional().saveAs("roomUuid"))),
            doIf(session -> session.contains("roomUuid")).then(
                    exec(http("GET /chat/rooms/{uuid}/messages").get("/api/v1/chat/rooms/#{roomUuid}/messages")
                            .header("Authorization", ProdFullReadScenarioSimulation::authHeader)
                            .check(status().is(200))),
                    exec(http("GET /chat/rooms/{uuid}/unread-count").get("/api/v1/chat/rooms/#{roomUuid}/unread-count")
                            .header("Authorization", ProdFullReadScenarioSimulation::authHeader)
                            .check(status().is(200)))));

    private static final ChainBuilder notificationCenterAuth = group("notification center (auth)").on(
            exec(http("GET /notification-center/notices").get("/api/v1/notification-center/notices")
                    .header("Authorization", ProdFullReadScenarioSimulation::authHeader)
                    .check(status().is(200))),
            exec(http("GET /notification-center/received-applications").get("/api/v1/notification-center/received-applications")
                    .header("Authorization", ProdFullReadScenarioSimulation::authHeader)
                    .check(status().is(200))),
            exec(http("GET /notification-center/sent-applications").get("/api/v1/notification-center/sent-applications")
                    .header("Authorization", ProdFullReadScenarioSimulation::authHeader)
                    .check(status().is(200))));

    private static final ScenarioBuilder scn = scenario("Prod full read sweep")
            .during(Duration.ofSeconds(140)).on(
                    publicNoAuth, shortPause(),
                    userProfileAuth, shortPause(),
                    postsAuth, shortPause(),
                    planAuthBestEffort, shortPause(),
                    partyAuthBestEffort, shortPause(),
                    chatAuth, shortPause(),
                    notificationCenterAuth,
                    pause(Duration.ofSeconds(1), Duration.ofSeconds(2)));

    {
        setUp(scn.injectClosed(buildCapacitySweepProfile()))
                .protocols(httpProtocol)
                .assertions(
                        global().responseTime().percentile(95.0).lt(800),
                        global().failedRequests().percent().lt(5.0));
    }
}
