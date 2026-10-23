package com.dogGetDrunk.meetjyou.post

import com.dogGetDrunk.meetjyou.post.dto.PostAuthorSummary
import com.dogGetDrunk.meetjyou.user.support.UserFixtures
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.time.Instant

class PostAuthorSummaryTest : BehaviorSpec({

    given("게시글 작성자 요약을 만들 때") {
        `when`("작성자가 정상(NORMAL) 유저면") {
            then("닉네임과 프로필 이미지 유무를 그대로 담는다") {
                val author = UserFixtures.user(nickname = NICKNAME).apply { hasProfileImage = true }

                PostAuthorSummary.of(author) shouldBe PostAuthorSummary(
                    uuid = author.uuid,
                    nickname = NICKNAME,
                    hasProfileImage = true,
                    isWithdrawn = false,
                )
            }
        }

        `when`("작성자가 탈퇴 유예 기간 중(DELETED)이면") {
            then("아직 익명화 전인 실제 닉네임과 프로필 이미지를 숨긴다") {
                val author = UserFixtures.user(nickname = NICKNAME).apply {
                    hasProfileImage = true
                    withdraw(Instant.now())
                }

                PostAuthorSummary.of(author) shouldBe PostAuthorSummary(
                    uuid = author.uuid,
                    nickname = PostAuthorSummary.WITHDRAWN_NICKNAME,
                    hasProfileImage = false,
                    isWithdrawn = true,
                )
            }
        }

        `when`("작성자가 탈퇴 완료(PURGED)돼 익명화됐으면") {
            then("익명화된 닉네임도 내보내지 않고 탈퇴로 표시한다") {
                val author = UserFixtures.user(nickname = NICKNAME).apply {
                    withdraw(Instant.now())
                    anonymize()
                }

                PostAuthorSummary.of(author) shouldBe PostAuthorSummary(
                    uuid = author.uuid,
                    nickname = PostAuthorSummary.WITHDRAWN_NICKNAME,
                    hasProfileImage = false,
                    isWithdrawn = true,
                )
            }
        }
    }
}) {
    companion object {
        private const val NICKNAME = "author1"
    }
}
