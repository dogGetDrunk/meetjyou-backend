package com.dogGetDrunk.meetjyou.loadtest;

import io.gatling.javaapi.core.ChainBuilder;
import io.gatling.javaapi.core.ClosedInjectionStep;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

// Capacity sweep against production using only permitAll GET endpoints, so it needs no
// load-test secret (unlike ProdFullReadScenarioSimulation). Every endpoint here still goes
// through the app and the cross-cloud MySQL, so it can locate a DB-side or app-side ceiling
// (issue #154) while server metrics are captured on both hosts.
//
// Closed model on purpose: concurrency is capped at the VU count, so going past the ceiling
// only grows latency instead of piling up an unbounded queue on a live service.
// Think time is shorter than the full-read scenario (~1.25s per iteration vs ~3s) so fewer
// VUs reach the same request rate.
//
// Run: ./gradlew gatlingRun --simulation com.dogGetDrunk.meetjyou.loadtest.ProdPublicReadSweepSimulation
public class ProdPublicReadSweepSimulation extends Simulation {

    private static final String BASE_URL = System.getProperty("BASE_URL", "https://meetjyou.duckdns.org");

    private static final HttpProtocolBuilder httpProtocol = http.baseUrl(BASE_URL);

    private record Stage(int targetVUs, Duration rampDuration, Duration holdDuration) {
    }

    private static final Duration RAMP = Duration.ofSeconds(10);
    private static final Duration HOLD = Duration.ofSeconds(40);
    private static final Duration RAMP_DOWN_DURATION = Duration.ofSeconds(10);

    private static final List<Stage> CAPACITY_SWEEP_STAGES = List.of(
            new Stage(10, RAMP, HOLD),
            new Stage(25, RAMP, HOLD),
            new Stage(50, RAMP, HOLD),
            new Stage(75, RAMP, HOLD),
            new Stage(100, RAMP, HOLD));

    private static final Duration SCENARIO_DURATION = Duration.ofSeconds(270);

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

    private static ChainBuilder shortPause() {
        return pause(Duration.ZERO, Duration.ofMillis(200));
    }

    private static final ChainBuilder publicReads = group("public (no auth)").on(
            exec(http("GET /notices").get("/api/v1/notices?page=0&size=20")
                    .check(status().is(200))
                    .check(jsonPath("$.content[0].uuid").optional().saveAs("noticeUuid"))),
            shortPause(),
            doIf(session -> session.contains("noticeUuid")).then(
                    exec(http("GET /notices/{uuid}").get("/api/v1/notices/#{noticeUuid}")
                            .check(status().is(200)))),
            shortPause(),
            exec(http("GET /terms/active").get("/api/v1/terms/active").check(status().is(200))),
            shortPause(),
            exec(http("GET /android/versions/check").get("/api/v1/android/versions/check?clientVersion=1.0.0")
                    .check(status().in(200, 400))),
            shortPause(),
            exec(http("GET /android/versions/latest").get("/api/v1/android/versions/latest")
                    .check(status().in(200, 404))),
            shortPause(),
            exec(http("GET /users/is-duplicate-nickname").get(session ->
                            "/api/v1/users/is-duplicate-nickname?nickname=gatlingprobe"
                                    + ThreadLocalRandom.current().nextInt(1_000_000))
                    .check(status().is(200))));

    private static final ScenarioBuilder scn = scenario("Prod public read sweep")
            .during(SCENARIO_DURATION).on(
                    publicReads,
                    pause(Duration.ofMillis(500), Duration.ofMillis(1000)));

    @Override
    public void before() {
        long elapsed = 0;
        Instant start = Instant.now();
        for (Stage stage : CAPACITY_SWEEP_STAGES) {
            System.out.printf("stage %3d VU: ramp from +%ds (%s), hold from +%ds%n",
                    stage.targetVUs(), elapsed, start.plusSeconds(elapsed),
                    elapsed + stage.rampDuration().toSeconds());
            elapsed += stage.rampDuration().toSeconds() + stage.holdDuration().toSeconds();
        }
    }

    {
        setUp(scn.injectClosed(buildCapacitySweepProfile()))
                .protocols(httpProtocol)
                .maxDuration(Duration.ofMinutes(6))
                .assertions(
                        global().responseTime().percentile(95.0).lt(800),
                        global().failedRequests().percent().lt(5.0));
    }
}
