package com.dogGetDrunk.meetjyou.common.idempotency

import com.dogGetDrunk.meetjyou.common.exception.business.idempotency.IdempotencyKeyConflictException
import com.dogGetDrunk.meetjyou.post.dto.CreatePostRequest
import com.dogGetDrunk.meetjyou.user.support.UserFixtures
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import java.util.UUID

class IdempotencyKeyServiceTest : BehaviorSpec() {

    private val idempotencyKeyRepository = mockk<IdempotencyKeyRepository>(relaxed = true)
    private val objectMapper = ObjectMapper().registerKotlinModule().registerModule(JavaTimeModule())
    private val sut = IdempotencyKeyService(idempotencyKeyRepository, objectMapper)

    override fun isolationMode() = IsolationMode.InstancePerLeaf

    private fun samplePostRequest(itinStart: Instant = Instant.now().plusSeconds(3600)) = CreatePostRequest(
        title = "Trip",
        content = "content",
        isInstant = false,
        itinStart = itinStart,
        itinFinish = itinStart.plusSeconds(3600),
        location = "Seoul",
        capacity = 4,
        companionSpec = null,
        planUuid = null,
        isPlanPublic = null,
    )

    init {
        beforeEach { clearAllMocks() }

        given("hashRequest 호출 시") {
            `when`("같은 요청 바디를 시간차를 두고 두 번 해싱하면") {
                then("동일한 해시가 나온다 (isItinStartAfterNow의 Instant.now() 의존성이 새어 들어가지 않음을 검증)") {
                    val request = samplePostRequest()

                    val first = sut.hashRequest(request)
                    val second = sut.hashRequest(request)

                    first shouldBe second
                }
            }

            `when`("직렬화 결과를 들여다보면") {
                then("@AssertTrue 검증 메서드 유래 프로퍼티가 섞여있지 않다 (@JsonIgnore 회귀 방지)") {
                    val json = objectMapper.writeValueAsString(samplePostRequest())

                    json.contains("itinStartAfterNow") shouldBe false
                    json.contains("itinFinishAfterItinStart") shouldBe false
                }
            }

            `when`("바디가 다르면") {
                then("다른 해시가 나온다") {
                    val a = sut.hashRequest(samplePostRequest(Instant.parse("2026-05-01T00:00:00Z")))
                    val b = sut.hashRequest(samplePostRequest(Instant.parse("2026-06-01T00:00:00Z")))

                    a shouldBe a
                    (a == b) shouldBe false
                }
            }
        }

        given("resolveExisting 호출 시") {
            val user = UserFixtures.user()
            val hash = "hash-value"

            `when`("매치되는 row가 없으면") {
                then("null을 반환한다") {
                    every {
                        idempotencyKeyRepository.findByScopeAndUser_IdAndIdempotencyKey(
                            IdempotencyScope.CREATE_POST, user.id, "key-1",
                        )
                    } returns null

                    val result = sut.resolveExisting(IdempotencyScope.CREATE_POST, user, "key-1", hash)

                    result shouldBe null
                }
            }

            `when`("매치되는 row가 있고 해시가 같으면") {
                then("기존 resourceUuid를 반환한다") {
                    val resourceUuid = UUID.randomUUID()
                    val existing = IdempotencyKey(
                        scope = IdempotencyScope.CREATE_POST,
                        user = user,
                        idempotencyKey = "key-2",
                        resourceUuid = resourceUuid,
                        requestHash = hash,
                    )
                    every {
                        idempotencyKeyRepository.findByScopeAndUser_IdAndIdempotencyKey(
                            IdempotencyScope.CREATE_POST, user.id, "key-2",
                        )
                    } returns existing

                    val result = sut.resolveExisting(IdempotencyScope.CREATE_POST, user, "key-2", hash)

                    result shouldBe resourceUuid
                }
            }

            `when`("매치되는 row가 있는데 해시가 다르면") {
                then("IdempotencyKeyConflictException을 던진다") {
                    val existing = IdempotencyKey(
                        scope = IdempotencyScope.CREATE_POST,
                        user = user,
                        idempotencyKey = "key-3",
                        resourceUuid = UUID.randomUUID(),
                        requestHash = "old-hash",
                    )
                    every {
                        idempotencyKeyRepository.findByScopeAndUser_IdAndIdempotencyKey(
                            IdempotencyScope.CREATE_POST, user.id, "key-3",
                        )
                    } returns existing

                    shouldThrow<IdempotencyKeyConflictException> {
                        sut.resolveExisting(IdempotencyScope.CREATE_POST, user, "key-3", "new-hash")
                    }
                }
            }
        }

        given("record 호출 시") {
            `when`("정상적으로 호출되면") {
                then("IdempotencyKeyRepository.save가 1회 호출된다") {
                    val user = UserFixtures.user()
                    val resourceUuid = UUID.randomUUID()
                    every { idempotencyKeyRepository.save(any()) } returnsArgument 0

                    sut.record(IdempotencyScope.CREATE_POST, user, "key-4", resourceUuid, "hash-4")

                    verify(exactly = 1) { idempotencyKeyRepository.save(any()) }
                }
            }
        }
    }
}
