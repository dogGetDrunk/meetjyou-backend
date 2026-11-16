package com.dogGetDrunk.meetjyou.user

import com.dogGetDrunk.meetjyou.user.dto.UserDisplay
import com.dogGetDrunk.meetjyou.user.support.UserFixtures
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.time.Instant

// AT-001: one display rule for every response that shows another user next to shared content.
class UserDisplayTest : BehaviorSpec({

    given("다른 회원의 표시 정보를 만들 때") {
        `when`("정상(NORMAL) 회원이면") {
            then("닉네임과 프로필 사진 유무를 그대로 담는다") {
                val user = UserFixtures.user(nickname = NICKNAME).apply { hasProfileImage = true }

                UserDisplay.of(user) shouldBe SHOWN
            }
        }

        `when`("탈퇴 유예 기간 중(DELETED)이면") {
            then("복구할 수 있으므로 아직 가리지 않는다") {
                val user = UserFixtures.user(nickname = NICKNAME).apply {
                    hasProfileImage = true
                    withdraw(Instant.now())
                }

                UserDisplay.of(user) shouldBe SHOWN
            }
        }

        `when`("완전 탈퇴(PURGED)돼 익명화됐으면") {
            then("익명화 닉네임 대신 탈퇴 문구로 가리고 사진도 숨긴다") {
                val user = UserFixtures.user(nickname = NICKNAME).apply {
                    hasProfileImage = true
                    withdraw(Instant.now())
                    anonymize()
                }

                UserDisplay.of(user) shouldBe UserDisplay(
                    nickname = "(탈퇴한 사용자)",
                    hasProfileImage = false,
                    isWithdrawn = true,
                )
            }
        }
    }
}) {
    companion object {
        private const val NICKNAME = "member1"
        private val SHOWN = UserDisplay(nickname = NICKNAME, hasProfileImage = true, isWithdrawn = false)
    }
}
