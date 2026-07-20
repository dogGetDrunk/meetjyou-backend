package com.dogGetDrunk.meetjyou.loadtest;

import io.gatling.javaapi.core.ChainBuilder;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

// Comprehensive load test for meetjyou-backend local dev instance.
// Covers every REST endpoint that is safe to hit from a local dev DB: auth, user profile,
// post, plan, marker, party (join/approve/reject), chat (read-side), push-token,
// notification-center, notice (read-only), terms (read-only), version (read-only).
//
// Deliberately EXCLUDED:
// - OracleObjectStorageController (all /*/img/par/* endpoints) — these call real OCI Object
//   Storage even under the dev profile (no mock), so they are out of scope for a local test.
// - Notice/Terms(write)/Version(write) admin endpoints and GET /users (all admin-only,
//   @PreAuthorize ADMIN) — except addVersion, which the before() hook calls once to seed a
//   version row (see below); it never touches OCI, unlike Terms.publishTerms.
// - Chat message send (ChatController.handleMessage is a STOMP @MessageMapping, not REST —
//   needs a SockJS/STOMP client, see .claude/rules/chat.md for the integration-test pattern).
// - PartyController.banMember / deleteParty / leaveParty / completeParty — destructive to the
//   shared party the user itself owns and not needed to exercise the read/write paths.
//
// KNOWN GAP: PATCH /users/me/marketing-consent 404s on a fresh DB because it requires a
// published Terms(type=MARKETING_SNS_EVENTS) row, and Terms.publishTerms validates the content
// was actually uploaded to OCI (hash check) — there's no way to seed it without OCI, so this
// check is intentionally left accepting 404 rather than treated as a failure.
//
// Requires: SPRING_PROFILES_ACTIVE=dev,db,secrets (dev bypass enabled)
// Run: ./gradlew gatlingRun --simulation com.dogGetDrunk.meetjyou.loadtest.FullApiScenarioSimulation
// Override target: ./gradlew gatlingRun --simulation ... -DBASE_URL=http://localhost:8080
//
// Design note vs. the original k6 script: like MixedScenarioSimulation, "register once, then
// loop for the life of the virtual user" is reproduced as register -> during(200s).on(...) for
// the main journey. The k6 script's per-group Trend metrics (group_profile_setup_ms, etc.) have
// no direct Gatling equivalent — Gatling's HTML report already breaks down min/mean/percentiles
// per request name, which is a finer-grained version of the same signal. The k6 auth_lifecycle
// scenario's startTime: '5s' offset (to avoid invalidating a session mid-journey) is not
// reproduced — Gatling's closed injection model has no direct "start after N seconds" step, and
// journey/lifecycle already use disjoint user pools with independent sessions, so the race this
// guarded against does not apply here.
public class FullApiScenarioSimulation extends Simulation {

    private static final String BASE_URL = System.getProperty("BASE_URL", "http://localhost:8080");
    private static final String ADMIN_PASSPHRASE = System.getProperty("ADMIN_PASSPHRASE", "dev-admin-passphrase");

    private static final HttpProtocolBuilder httpProtocol = http.baseUrl(BASE_URL)
            .contentTypeHeader("application/json");

    private static String uniqueSuffix() {
        return System.currentTimeMillis() + "-" + ThreadLocalRandom.current().nextInt(100_000);
    }

    // Post/plan titles are server-validated to @Size(max = 20) (CreatePostRequest/CreatePlanRequest).
    // uniqueSuffix() alone overflows that, so title uniqueness uses this short 6-digit variant
    // instead — mirrors the original k6 script's `.slice(0, 20)` truncation.
    private static String shortTitleSuffix() {
        return String.valueOf(ThreadLocalRandom.current().nextInt(100_000, 1_000_000));
    }

    // nickname is capped at 8 chars server-side.
    private static String uniqueNickname() {
        return ("u" + Long.toString(ThreadLocalRandom.current().nextLong() & Long.MAX_VALUE, 36)).substring(0, 8);
    }

    private static String extractJsonString(String json, String field) {
        Matcher matcher = Pattern.compile("\"" + field + "\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        return matcher.find() ? matcher.group(1) : null;
    }

    // Seeds an AppVersion(platform=ANDROID, version=1.0.0) row via the admin API, outside the
    // load-generation engine, so PushTokenController.register (which validates appVersion
    // against this table) doesn't 404 on a fresh DB. addVersion is local-DB-only (no OCI call),
    // unlike Terms.publishTerms. Best-effort: a failure here only means the push-token group's
    // register call may itself 404, so it is logged, not fatal.
    @Override
    public void before() {
        try {
            HttpClient client = HttpClient.newHttpClient();
            String regBody = "{\"email\":\"loadtest-setup-" + uniqueSuffix() + "@local.test\",\"nickname\":\"setupadm\"}";
            HttpResponse<String> regRes = client.send(
                    HttpRequest.newBuilder()
                            .uri(URI.create(BASE_URL + "/api/v1/dev/auth/register"))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(regBody))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            if (regRes.statusCode() != 201) {
                System.out.println("setup: dev register failed (" + regRes.statusCode() + "), version seed skipped");
                return;
            }
            String setupAccessToken = extractJsonString(regRes.body(), "accessToken");

            HttpResponse<String> promoteRes = client.send(
                    HttpRequest.newBuilder()
                            .uri(URI.create(BASE_URL + "/api/v1/auth/promote-admin"))
                            .header("Authorization", "Bearer " + setupAccessToken)
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString("{\"passphrase\":\"" + ADMIN_PASSPHRASE + "\"}"))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            if (promoteRes.statusCode() != 200) {
                System.out.println("setup: promote-admin failed (" + promoteRes.statusCode() + "), version seed skipped");
                return;
            }
            String adminToken = extractJsonString(promoteRes.body(), "accessToken");

            String versionBody = "{\"platform\":\"ANDROID\",\"version\":\"1.0.0\",\"forceUpdate\":false,"
                    + "\"message\":null,\"storeReleased\":true,\"releasedAt\":\"2026-01-01T00:00:00Z\"}";
            HttpResponse<String> versionRes = client.send(
                    HttpRequest.newBuilder()
                            .uri(URI.create(BASE_URL + "/api/v1/android/versions"))
                            .header("Authorization", "Bearer " + adminToken)
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(versionBody))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            if (versionRes.statusCode() != 201 && versionRes.statusCode() != 409) {
                System.out.println("setup: addVersion failed (" + versionRes.statusCode() + "): " + versionRes.body());
            }
        } catch (IOException | InterruptedException e) {
            System.out.println("setup: version seed skipped due to exception: " + e.getMessage());
        }
    }

    // Registers a fresh dev user under session keys scoped by `tag`, so the same building block
    // can register a "vu" identity and a throwaway "churn" identity within one scenario run
    // without the two registrations clobbering each other's session state.
    private static ChainBuilder registerAs(String tag, String uuidVar, String accessTokenVar, String refreshTokenVar, String nicknameVar) {
        String emailKey = "regEmail_" + tag;
        String nicknameKey = "regNickname_" + tag;
        return exec(session -> session
                        .set(emailKey, "loadtest-" + tag + "-" + uniqueSuffix() + "@local.test")
                        .set(nicknameKey, uniqueNickname()))
                .exec(http("POST /dev/auth/register (" + tag + ")")
                        .post("/api/v1/dev/auth/register")
                        .body(StringBody(session -> "{\"email\":\"" + session.getString(emailKey)
                                + "\",\"nickname\":\"" + session.getString(nicknameKey) + "\"}"))
                        .check(status().is(201))
                        .check(jsonPath("$.uuid").saveAs(uuidVar))
                        .check(jsonPath("$.accessToken").saveAs(accessTokenVar))
                        .check(jsonPath("$.refreshToken").saveAs(refreshTokenVar)))
                .exec(session -> session.set(nicknameVar, session.getString(nicknameKey)));
    }

    private static final ChainBuilder profileSetup = group("profile setup").on(
            exec(http("GET /users/is-duplicate-nickname").get("/api/v1/users/is-duplicate-nickname?nickname=#{nickname}")
                    .header("Authorization", "Bearer #{accessToken}")
                    .check(status().is(200))),
            exec(http("PUT /users/me").put("/api/v1/users/me")
                    .header("Authorization", "Bearer #{accessToken}")
                    .body(StringBody("{\"nickname\":\"#{nickname}\",\"bio\":\"Gatling load test bio\",\"gender\":\"O\","
                            + "\"age\":\"TWENTY\",\"personalities\":[\"INTROVERTED\",\"EXTROVERTED\"],"
                            + "\"travelStyles\":[\"ACTIVITY\",\"RELAX\"],\"diet\":[\"VEGETARIAN\"],\"etc\":[\"SMOKE\"]}"))
                    .check(status().is(200))),
            // 404 is expected on a DB with no published MARKETING_SNS_EVENTS terms — see KNOWN GAP above.
            exec(http("PATCH /users/me/marketing-consent").patch("/api/v1/users/me/marketing-consent")
                    .header("Authorization", "Bearer #{accessToken}")
                    .body(StringBody("{\"snsConsented\":true,\"emailConsented\":false}"))
                    .check(status().in(204, 404))),
            exec(http("GET /users/me/notification-settings").get("/api/v1/users/me/notification-settings")
                    .header("Authorization", "Bearer #{accessToken}")
                    .check(status().is(200))),
            exec(http("PUT /users/me/notification-settings").put("/api/v1/users/me/notification-settings")
                    .header("Authorization", "Bearer #{accessToken}")
                    .body(StringBody("{\"globalEnabled\":true,\"categories\":null}"))
                    .check(status().is(204))),
            exec(http("GET /users/me/profile").get("/api/v1/users/me/profile")
                    .header("Authorization", "Bearer #{accessToken}")
                    .check(status().in(200, 404))));

    private static final ChainBuilder browse = group("browse").on(
            exec(http("GET /posts").get("/api/v1/posts?page=0&size=20")
                    .header("Authorization", "Bearer #{accessToken}")
                    .check(status().is(200))),
            exec(http("GET /users/me/posts").get("/api/v1/users/me/posts")
                    .header("Authorization", "Bearer #{accessToken}")
                    .check(status().is(200))),
            exec(http("GET /users/me/plans").get("/api/v1/users/me/plans")
                    .header("Authorization", "Bearer #{accessToken}")
                    .check(status().is(200))),
            exec(http("GET /users/me/parties").get("/api/v1/users/me/parties")
                    .header("Authorization", "Bearer #{accessToken}")
                    .check(status().is(200))),
            exec(http("GET /users/me/applications").get("/api/v1/users/me/applications")
                    .header("Authorization", "Bearer #{accessToken}")
                    .check(status().is(200))),
            exec(http("GET /chat/rooms").get("/api/v1/chat/rooms")
                    .header("Authorization", "Bearer #{accessToken}")
                    .check(status().is(200))),
            exec(http("GET /notices").get("/api/v1/notices?page=0&size=10")
                    .header("Authorization", "Bearer #{accessToken}")
                    .check(status().is(200))),
            exec(http("GET /terms/active").get("/api/v1/terms/active")
                    .header("Authorization", "Bearer #{accessToken}")
                    .check(status().is(200))),
            exec(http("GET /android/versions/latest").get("/api/v1/android/versions/latest")
                    .header("Authorization", "Bearer #{accessToken}")
                    .check(status().in(200, 404))),
            exec(http("GET /users/{uuid}/basic-info").get("/api/v1/users/#{uuid}/basic-info")
                    .header("Authorization", "Bearer #{accessToken}")
                    .check(status().is(200))),
            exec(http("GET /users/{uuid}/advanced-info").get("/api/v1/users/#{uuid}/advanced-info")
                    .header("Authorization", "Bearer #{accessToken}")
                    .check(status().is(200))));

    private static final ChainBuilder contentCreation = group("content creation").on(
            exec(http("POST /posts (create)").post("/api/v1/posts")
                    .header("Authorization", "Bearer #{accessToken}")
                    .body(StringBody(session -> "{\"title\":\"Gatling trip " + shortTitleSuffix() + "\","
                            + "\"content\":\"Generated by Gatling full-api load test\",\"isInstant\":false,"
                            + "\"itinStart\":\"2026-08-01T00:00:00Z\",\"itinFinish\":\"2026-08-05T00:00:00Z\","
                            + "\"location\":\"제주\",\"capacity\":4}"))
                    .check(status().is(200))
                    .check(jsonPath("$.uuid").optional().saveAs("ownPostUuid"))
                    .check(jsonPath("$.chatRoom.uuid").optional().saveAs("ownRoomUuid"))),
            doIf(session -> session.contains("ownPostUuid")).then(
                    exec(http("GET /posts/{uuid} (own)").get("/api/v1/posts/#{ownPostUuid}")
                            .header("Authorization", "Bearer #{accessToken}")
                            .check(status().is(200))
                            .check(jsonPath("$.partyUuid").optional().saveAs("ownPartyUuid"))),
                    exec(http("PUT /posts/{uuid} (update)").put("/api/v1/posts/#{ownPostUuid}")
                            .header("Authorization", "Bearer #{accessToken}")
                            .body(StringBody("{\"title\":\"Gatling trip v2\",\"content\":\"Updated by Gatling full-api load test\","
                                    + "\"isInstant\":false,\"itinStart\":\"2026-08-01T00:00:00Z\","
                                    + "\"itinFinish\":\"2026-08-06T00:00:00Z\",\"location\":\"제주\",\"capacity\":4}"))
                            .check(status().is(200)))),
            exec(http("POST /plans (create)").post("/api/v1/plans")
                    .header("Authorization", "Bearer #{accessToken}")
                    .body(StringBody(session -> "{\"title\":\"Gatling plan " + shortTitleSuffix() + "\","
                            + "\"itinStart\":\"2026-08-01T00:00:00Z\",\"itinFinish\":\"2026-08-05T00:00:00Z\","
                            + "\"location\":\"제주\",\"centerLat\":33.4996,\"centerLng\":126.5312,\"markers\":[]}"))
                    .check(status().is(200))
                    .check(jsonPath("$.uuid").optional().saveAs("ownPlanUuid"))),
            doIf(session -> session.contains("ownPlanUuid")).then(
                    exec(http("PUT /plans/{uuid}/markers (replace)").put("/api/v1/plans/#{ownPlanUuid}/markers")
                            .header("Authorization", "Bearer #{accessToken}")
                            .body(StringBody("{\"markers\":[{\"lat\":33.5,\"lng\":126.53,\"date\":\"2026-08-01T00:00:00Z\","
                                    + "\"dayNum\":1,\"idx\":0,\"place\":\"공항\",\"memo\":null}]}"))
                            .check(status().is(200))),
                    exec(http("GET /plans/{uuid}/markers").get("/api/v1/plans/#{ownPlanUuid}/markers")
                            .header("Authorization", "Bearer #{accessToken}")
                            .check(status().is(200))),
                    exec(http("PUT /plans/{uuid} (update)").put("/api/v1/plans/#{ownPlanUuid}")
                            .header("Authorization", "Bearer #{accessToken}")
                            .body(StringBody("{\"title\":\"Gatling plan v2\",\"itinStart\":\"2026-08-01T00:00:00Z\","
                                    + "\"itinFinish\":\"2026-08-06T00:00:00Z\",\"location\":\"제주\",\"centerLat\":33.4996,"
                                    + "\"centerLng\":126.5312,\"memo\":\"updated\",\"favorite\":true}"))
                            .check(status().is(200)))));

    // Try to find someone else's post to apply to. jsonPath filter-expression dialects are
    // inconsistent across implementations, so the two parallel arrays are pulled out with
    // findAll() and matched up in plain Java rather than relying on a `!=` filter predicate.
    private static final ChainBuilder socialInteraction = group("social interaction").on(
            exec(http("GET /posts (social)").get("/api/v1/posts?page=0&size=20")
                    .header("Authorization", "Bearer #{accessToken}")
                    .check(status().is(200))
                    .check(jsonPath("$.content[*].authorUuid").findAll().optional().saveAs("socialAuthorUuids"))
                    .check(jsonPath("$.content[*].partyUuid").findAll().optional().saveAs("socialPartyUuids"))),
            exec(session -> {
                List<String> authorUuids = session.getList("socialAuthorUuids");
                List<String> partyUuids = session.getList("socialPartyUuids");
                String ownUuid = session.getString("uuid");
                if (authorUuids == null || partyUuids == null) {
                    return session;
                }
                for (int i = 0; i < authorUuids.size() && i < partyUuids.size(); i++) {
                    if (!authorUuids.get(i).equals(ownUuid)) {
                        return session.set("otherPartyUuid", partyUuids.get(i));
                    }
                }
                return session;
            }),
            doIf(session -> session.contains("otherPartyUuid")).then(
                    exec(http("POST /parties/{uuid}/join-requests").post("/api/v1/parties/#{otherPartyUuid}/join-requests")
                            .header("Authorization", "Bearer #{accessToken}")
                            .body(StringBody("{\"applicationNote\":\"Gatling load test application\"}"))
                            .check(status().in(201, 400, 409)))),
            doIf(session -> session.contains("ownPartyUuid")).then(
                    exec(http("GET /parties/{uuid}/members").get("/api/v1/parties/#{ownPartyUuid}/members")
                            .header("Authorization", "Bearer #{accessToken}")
                            .check(status().is(200))),
                    exec(http("GET /parties/{uuid}/join-requests (pending)").get("/api/v1/parties/#{ownPartyUuid}/join-requests")
                            .header("Authorization", "Bearer #{accessToken}")
                            .check(status().is(200))
                            .check(jsonPath("$.requests[0].userUuid").optional().saveAs("pendingApplicantUuid"))),
                    doIf(session -> session.contains("pendingApplicantUuid")).then(
                            exec(http("POST /parties/{uuid}/join-requests/{userUuid}/approve")
                                    .post("/api/v1/parties/#{ownPartyUuid}/join-requests/#{pendingApplicantUuid}/approve")
                                    .header("Authorization", "Bearer #{accessToken}")
                                    .check(status().in(204, 404))))));

    private static final ChainBuilder pushAndChatRead = group("push & chat read").on(
            exec(session -> session.set("pushToken", "gatling-fake-token-" + uniqueSuffix())),
            exec(http("POST /push-tokens (register)").post("/api/v1/push-tokens")
                    .header("Authorization", "Bearer #{accessToken}")
                    .body(StringBody("{\"token\":\"#{pushToken}\",\"platform\":\"ANDROID\",\"appVersion\":\"1.0.0\",\"deviceModel\":\"gatling-virtual\"}"))
                    .check(status().is(200))),
            exec(http("DELETE /push-tokens (deactivate)").delete("/api/v1/push-tokens?token=#{pushToken}")
                    .header("Authorization", "Bearer #{accessToken}")
                    .check(status().in(200, 204))),
            doIf(session -> session.contains("ownRoomUuid")).then(
                    exec(http("GET /chat/rooms/{uuid}/messages").get("/api/v1/chat/rooms/#{ownRoomUuid}/messages")
                            .header("Authorization", "Bearer #{accessToken}")
                            .check(status().is(200))),
                    exec(http("GET /chat/rooms/{uuid}/unread-count").get("/api/v1/chat/rooms/#{ownRoomUuid}/unread-count")
                            .header("Authorization", "Bearer #{accessToken}")
                            .check(status().is(200))),
                    exec(http("POST /chat/rooms/{uuid}/read").post("/api/v1/chat/rooms/#{ownRoomUuid}/read")
                            .header("Authorization", "Bearer #{accessToken}")
                            .check(status().in(200, 204)))));

    private static final ChainBuilder notificationCenter = group("notification center").on(
            exec(http("GET /notification-center/notices").get("/api/v1/notification-center/notices")
                    .header("Authorization", "Bearer #{accessToken}")
                    .check(status().is(200))),
            exec(http("POST /notification-center/notices/read").post("/api/v1/notification-center/notices/read")
                    .header("Authorization", "Bearer #{accessToken}")
                    .check(status().in(200, 204))),
            exec(http("GET /notification-center/received-applications").get("/api/v1/notification-center/received-applications")
                    .header("Authorization", "Bearer #{accessToken}")
                    .check(status().is(200))),
            exec(http("POST /notification-center/received-applications/read").post("/api/v1/notification-center/received-applications/read")
                    .header("Authorization", "Bearer #{accessToken}")
                    .check(status().in(200, 204))),
            exec(http("GET /notification-center/sent-applications").get("/api/v1/notification-center/sent-applications")
                    .header("Authorization", "Bearer #{accessToken}")
                    .check(status().is(200))),
            exec(http("POST /notification-center/sent-applications/read").post("/api/v1/notification-center/sent-applications/read")
                    .header("Authorization", "Bearer #{accessToken}")
                    .check(status().in(200, 204))));

    private static final ChainBuilder journeyLoopBody = exec(profileSetup)
            .pause(Duration.ZERO, Duration.ofSeconds(1))
            .exec(browse)
            .pause(Duration.ZERO, Duration.ofSeconds(1))
            .exec(contentCreation)
            .pause(Duration.ZERO, Duration.ofSeconds(1))
            .exec(socialInteraction)
            .pause(Duration.ZERO, Duration.ofSeconds(1))
            .exec(pushAndChatRead)
            .pause(Duration.ZERO, Duration.ofSeconds(1))
            .exec(notificationCenter)
            .pause(Duration.ZERO, Duration.ofMillis(1500));

    private static final ScenarioBuilder journey = scenario("Full API journey")
            .exec(registerAs("vu", "uuid", "accessToken", "refreshToken", "nickname"))
            .during(Duration.ofSeconds(200)).on(journeyLoopBody);

    // Exercises the token lifecycle in isolation: register -> refresh -> logout, then a fresh
    // registration -> withdraw. Kept as a separate scenario/user pool from `journey` so it
    // doesn't invalidate a session mid-scenario for the main journey users.
    private static final ChainBuilder lifecycleFlow = registerAs("life", "uuid", "accessToken", "refreshToken", "nickname")
            .exec(http("POST /auth/refresh").post("/api/v1/auth/refresh")
                    .header("Authorization", "Bearer #{refreshToken}")
                    .check(status().is(200))
                    .check(jsonPath("$.refreshToken").optional().saveAs("refreshedRefreshToken")))
            .exec(session -> session.set("logoutRefreshToken",
                    session.contains("refreshedRefreshToken") ? session.getString("refreshedRefreshToken") : session.getString("refreshToken")))
            .exec(http("POST /auth/logout").post("/api/v1/auth/logout")
                    .header("Authorization", "Bearer #{logoutRefreshToken}")
                    .check(status().is(204)))
            .pause(Duration.ofMillis(500))
            // Separate throwaway session to test withdrawal (soft delete) without affecting the one above.
            .exec(registerAs("churn", "churnUuid", "churnAccessToken", "churnRefreshToken", "churnNickname"))
            .exec(http("DELETE /users/me (withdraw)").delete("/api/v1/users/me")
                    .header("Authorization", "Bearer #{churnAccessToken}")
                    .check(status().in(200, 204)))
            .pause(Duration.ofSeconds(1));

    private static final ScenarioBuilder lifecycle = scenario("Auth lifecycle").repeat(3).on(lifecycleFlow);

    {
        setUp(
                journey.injectClosed(
                        rampConcurrentUsers(0).to(10).during(Duration.ofSeconds(20)),
                        constantConcurrentUsers(10).during(Duration.ofSeconds(40)),
                        rampConcurrentUsers(10).to(30).during(Duration.ofSeconds(20)),
                        constantConcurrentUsers(30).during(Duration.ofSeconds(40)),
                        rampConcurrentUsers(30).to(50).during(Duration.ofSeconds(20)),
                        constantConcurrentUsers(50).during(Duration.ofSeconds(40)),
                        rampConcurrentUsers(50).to(0).during(Duration.ofSeconds(20))),
                lifecycle.injectClosed(
                        constantConcurrentUsers(5).during(Duration.ofMinutes(3))))
                .protocols(httpProtocol)
                .assertions(
                        global().responseTime().percentile(95.0).lt(800),
                        global().failedRequests().percent().lt(5.0));
    }
}
