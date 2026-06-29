package com.dogGetDrunk.meetjyou.post.view

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Optional

class PostViewServiceTest : BehaviorSpec() {

    private val postViewCountRepository = mockk<PostViewCountRepository>(relaxed = true)
    private val postViewLogRepository = mockk<PostViewLogRepository>(relaxed = true)
    private val sut = PostViewService(postViewCountRepository, postViewLogRepository)

    override fun isolationMode() = IsolationMode.InstancePerLeaf

    init {
        val postId = 1L
        val userId = 10L

        beforeEach { clearAllMocks() }

        given("incrementIfEligible 호출 시") {
            `when`("조회 이력이 없는 경우") {
                then("조회수를 증가시키고 로그를 upsert한다") {
                    every { postViewLogRepository.findById(PostViewLogId(userId, postId)) } returns Optional.empty()

                    sut.incrementIfEligible(postId, userId)

                    verify(exactly = 1) { postViewLogRepository.upsertViewedAt(userId, postId, any()) }
                    verify(exactly = 1) { postViewCountRepository.upsertIncrement(postId) }
                }
            }

            `when`("10분 초과한 이전 조회 이력이 있는 경우") {
                then("조회수를 증가시키고 로그를 갱신한다") {
                    val oldLog = PostViewLog(
                        id = PostViewLogId(userId, postId),
                        viewedAt = Instant.now().minus(11, ChronoUnit.MINUTES),
                    )
                    every { postViewLogRepository.findById(PostViewLogId(userId, postId)) } returns Optional.of(oldLog)

                    sut.incrementIfEligible(postId, userId)

                    verify(exactly = 1) { postViewLogRepository.upsertViewedAt(userId, postId, any()) }
                    verify(exactly = 1) { postViewCountRepository.upsertIncrement(postId) }
                }
            }

            `when`("10분 이내 재조회인 경우") {
                then("조회수를 증가시키지 않는다") {
                    val recentLog = PostViewLog(
                        id = PostViewLogId(userId, postId),
                        viewedAt = Instant.now().minus(5, ChronoUnit.MINUTES),
                    )
                    every { postViewLogRepository.findById(PostViewLogId(userId, postId)) } returns Optional.of(recentLog)

                    sut.incrementIfEligible(postId, userId)

                    verify(exactly = 0) { postViewLogRepository.upsertViewedAt(any(), any(), any()) }
                    verify(exactly = 0) { postViewCountRepository.upsertIncrement(any()) }
                }
            }
        }

        given("getViewCount 호출 시") {
            `when`("카운트 레코드가 없는 경우") {
                then("0을 반환한다") {
                    every { postViewCountRepository.findById(postId) } returns Optional.empty()

                    sut.getViewCount(postId) shouldBe 0L
                }
            }

            `when`("카운트 레코드가 있는 경우") {
                then("저장된 조회수를 반환한다") {
                    every { postViewCountRepository.findById(postId) } returns Optional.of(PostViewCount(postId, 42L))

                    sut.getViewCount(postId) shouldBe 42L
                }
            }
        }
    }
}
