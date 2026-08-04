package com.dogGetDrunk.meetjyou.common.serialization

import com.dogGetDrunk.meetjyou.party.dto.CreatePartyRequest
import com.dogGetDrunk.meetjyou.party.dto.UpdatePartyRequest
import com.dogGetDrunk.meetjyou.plan.dto.UpdatePlanRequest
import com.dogGetDrunk.meetjyou.post.dto.UpdatePostRequest
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldNotContain
import java.time.Instant
import java.util.UUID

/**
 * Regression test for the same failure mode found and fixed in CreatePostRequest/CreatePlanRequest
 * (see IdempotencyKeyServiceTest): Jackson bean introspection treats any public is-prefixed no-arg
 * method as a serializable property, regardless of @AssertTrue/validation-only intent. These four
 * DTOs carry the identical pattern but had no serialization call site at the time - this test exists
 * so that if one is ever wired into ObjectMapper (idempotency hashing, logging, caching), the
 * regression is caught immediately instead of silently reproducing the same bug class.
 *
 * Assertions parse the actual key set rather than substring-matching the raw JSON: Jackson keeps
 * the method's "is" prefix verbatim (isItinStartAfterNow, not itinStartAfterNow), so a lowercase-i
 * substring check would never match and pass silently even with @JsonIgnore missing.
 */
class AssertTrueValidatorJsonIgnoreTest : BehaviorSpec({

    val objectMapper = ObjectMapper().registerKotlinModule().registerModule(JavaTimeModule())

    fun keysOf(json: String): Set<String> = objectMapper.readValue(json, Map::class.java).keys.map { it.toString() }.toSet()

    given("검증 전용 is-prefixed 메서드를 가진 요청 DTO를 직렬화하면") {
        `when`("UpdatePostRequest") {
            then("isItinStartAfterNow/isItinFinishAfterItinStart 키가 JSON에 없다") {
                val json = objectMapper.writeValueAsString(
                    UpdatePostRequest(
                        title = "title", content = "content", isInstant = false,
                        itinStart = Instant.now().plusSeconds(3600), itinFinish = Instant.now().plusSeconds(7200),
                        location = "Seoul", capacity = 4, companionSpec = null, planUuid = null, isPlanPublic = null,
                    )
                )
                val keys = keysOf(json)
                keys shouldNotContain "isItinStartAfterNow"
                keys shouldNotContain "isItinFinishAfterItinStart"
            }
        }

        `when`("CreatePartyRequest") {
            then("isItinStartAfterNow/isItinFinishAfterItinStart 키가 JSON에 없다") {
                val json = objectMapper.writeValueAsString(
                    CreatePartyRequest(
                        itinStart = Instant.now().plusSeconds(3600), itinFinish = Instant.now().plusSeconds(7200),
                        destination = "Seoul", joined = 1, capacity = 4, name = "Trip",
                        planUuid = null, ownerUuid = UUID.randomUUID(),
                    )
                )
                val keys = keysOf(json)
                keys shouldNotContain "isItinStartAfterNow"
                keys shouldNotContain "isItinFinishAfterItinStart"
            }
        }

        `when`("UpdatePartyRequest") {
            then("isItinStartAfterNow/isItinFinishAfterItinStart 키가 JSON에 없다") {
                val json = objectMapper.writeValueAsString(
                    UpdatePartyRequest(
                        itinStart = Instant.now().plusSeconds(3600), itinFinish = Instant.now().plusSeconds(7200),
                        destination = "Seoul", capacity = 4, name = "Trip", planUuid = null,
                    )
                )
                val keys = keysOf(json)
                keys shouldNotContain "isItinStartAfterNow"
                keys shouldNotContain "isItinFinishAfterItinStart"
            }
        }

        `when`("UpdatePlanRequest") {
            then("isItinFinishAfterItinStart 키가 JSON에 없다") {
                val json = objectMapper.writeValueAsString(
                    UpdatePlanRequest(
                        title = "title", itinStart = Instant.now().plusSeconds(3600), itinFinish = Instant.now().plusSeconds(7200),
                        location = "Seoul", centerLat = 37.5665, centerLng = 126.9780, memo = null, favorite = false,
                    )
                )
                keysOf(json) shouldNotContain "isItinFinishAfterItinStart"
            }
        }
    }
})
