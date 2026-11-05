package com.dogGetDrunk.meetjyou.plan

import com.dogGetDrunk.meetjyou.plan.dto.CreateMarkerRequest
import com.dogGetDrunk.meetjyou.plan.dto.CreatePlanRequest
import com.dogGetDrunk.meetjyou.plan.dto.ReplaceMarkersRequest
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import jakarta.validation.Validation
import jakarta.validation.Validator
import java.time.Instant

/**
 * AT-001-1 (REQ-001): marker request constraints, checked through a real jakarta Validator.
 * Annotation presence is deliberately not asserted; only the resulting violations are.
 */
class MarkerRequestValidationTest : BehaviorSpec({
    val validator: Validator = Validation.buildDefaultValidatorFactory().validator

    val maxMarkers = 200
    val maxPlace = 100
    val maxMemo = 500
    val itinStart = Instant.parse("2030-01-01T00:00:00Z")

    fun marker() = CreateMarkerRequest(
        lat = 37.5,
        lng = 127.0,
        date = itinStart,
        dayNum = 1,
        idx = 0,
        place = "Seoul",
        memo = "memo",
    )

    fun planRequest(markers: List<CreateMarkerRequest>) = CreatePlanRequest(
        title = "title",
        itinStart = itinStart,
        itinFinish = itinStart.plusSeconds(3600),
        location = "Seoul",
        centerLat = 37.5,
        centerLng = 127.0,
        memo = null,
        markers = markers,
    )

    // Each wrapper DTO is validated the same way; the label is only used in test names.
    val wrappers: Map<String, (List<CreateMarkerRequest>) -> Any> = mapOf(
        "CreatePlanRequest" to { markers -> planRequest(markers) },
        "ReplaceMarkersRequest" to { markers -> ReplaceMarkersRequest(markers) },
    )

    fun violationPaths(request: Any): List<String> =
        validator.validate(request).map { it.propertyPath.toString() }

    // (case name, violated field, invalid marker)
    val violationCases: List<Triple<String, String, CreateMarkerRequest>> = listOf(
        Triple("memo 501 chars", "memo", marker().copy(memo = "m".repeat(maxMemo + 1))),
        Triple("place 101 chars", "place", marker().copy(place = "p".repeat(maxPlace + 1))),
        Triple("blank place", "place", marker().copy(place = "   ")),
        Triple("lat 90.000001", "lat", marker().copy(lat = 90.000001)),
        Triple("lng -180.000001", "lng", marker().copy(lng = -180.000001)),
        Triple("dayNum 0", "dayNum", marker().copy(dayNum = 0)),
        Triple("idx -1", "idx", marker().copy(idx = -1)),
    )

    val boundaryCases: List<Pair<String, CreateMarkerRequest>> = listOf(
        "memo 500 chars" to marker().copy(memo = "m".repeat(maxMemo)),
        "memo null" to marker().copy(memo = null),
        "place 100 chars" to marker().copy(place = "p".repeat(maxPlace)),
        "lat 90" to marker().copy(lat = 90.0),
        "lat -90" to marker().copy(lat = -90.0),
        "lng 180" to marker().copy(lng = 180.0),
        "lng -180" to marker().copy(lng = -180.0),
        "dayNum 1" to marker().copy(dayNum = 1),
        "idx 0" to marker().copy(idx = 0),
    )

    wrappers.forEach { (wrapperName, build) ->
        Given("AT-001-1 $wrapperName with an invalid marker") {
            violationCases.forEach { (caseName, field, badMarker) ->
                When(caseName) {
                    val paths = violationPaths(build(listOf(marker(), badMarker)))

                    Then("a violation is reported on markers[1].$field") {
                        paths shouldContain "markers[1].$field"
                    }
                }
            }
        }

        Given("AT-001-1 $wrapperName with 201 markers") {
            When("validated") {
                val paths = violationPaths(build(List(maxMarkers + 1) { marker() }))

                Then("a violation is reported on markers") {
                    paths shouldContain "markers"
                }
            }
        }

        Given("AT-001-1 $wrapperName with valid boundary input") {
            boundaryCases.forEach { (caseName, boundaryMarker) ->
                When(caseName) {
                    val violations = violationPaths(build(listOf(boundaryMarker)))

                    Then("no violation") {
                        violations.shouldBeEmpty()
                    }
                }
            }

            When("exactly 200 markers") {
                val violations = violationPaths(build(List(maxMarkers) { marker() }))

                Then("no violation") {
                    violations.shouldBeEmpty()
                }
            }

            When("an empty marker list") {
                val violations = violationPaths(build(emptyList()))

                Then("no violation") {
                    violations.shouldBeEmpty()
                }
            }
        }
    }
})
