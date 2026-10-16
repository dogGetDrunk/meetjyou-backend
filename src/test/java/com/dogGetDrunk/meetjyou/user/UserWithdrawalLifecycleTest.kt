package com.dogGetDrunk.meetjyou.user

import com.dogGetDrunk.meetjyou.user.support.UserFixtures
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import java.time.Duration
import java.time.Instant

class UserWithdrawalLifecycleTest : BehaviorSpec({
    val now = Instant.parse("2026-10-14T00:00:00Z")

    given("탈퇴한 유저의 복구 가능 여부는") {
        `when`("탈퇴 후 유예기간이 아직 남았으면") {
            then("복구할 수 있다") {
                val user = UserFixtures.user().apply { withdraw(now.minus(Duration.ofDays(29))) }

                user.isRestorable(now) shouldBe true
            }
        }

        `when`("탈퇴 후 정확히 30일이 지났으면") {
            then("복구할 수 없다 (purge 대상 조건과 경계가 맞물린다)") {
                val user = UserFixtures.user().apply { withdraw(now.minus(User.WITHDRAWAL_GRACE_PERIOD)) }

                user.isRestorable(now) shouldBe false
            }
        }

        `when`("탈퇴 상태가 아니면") {
            then("복구 대상이 아니다") {
                UserFixtures.user().isRestorable(now) shouldBe false
            }
        }
    }

    given("유예기간 안에 탈퇴한 유저를") {
        `when`("복구하면") {
            then("NORMAL로 돌아가고 탈퇴 시각이 지워진다") {
                val user = UserFixtures.user().apply { withdraw(now) }

                user.restore()

                user.status shouldBe UserStatus.NORMAL
                user.withdrawnAt shouldBe null
                user.isActive() shouldBe true
            }
        }
    }

    given("유예기간이 끝난 유저를") {
        `when`("익명화하면") {
            then("개인정보를 지우고 레거시 external_id를 풀어 주며 PURGED로 바뀐다") {
                val user = UserFixtures.user().apply {
                    bio = "hello"
                    hasProfileImage = true
                    marketingSnsConsented = true
                    withdraw(now.minus(Duration.ofDays(31)))
                }

                user.anonymize()

                user.email shouldBe ""
                user.nickname shouldStartWith "~"
                user.externalId shouldBe "withdrawn:${user.uuid}"
                user.bio shouldBe null
                user.hasProfileImage shouldBe false
                user.marketingSnsConsented shouldBe false
                user.status shouldBe UserStatus.PURGED
                user.isActive() shouldBe false
            }
        }
    }

    given("익명화 닉네임은") {
        `when`("user.id가 INT 최댓값이어도") {
            then("nickname 컬럼(VARCHAR(10)) 안에 들어간다") {
                User.anonymizedNickname(Int.MAX_VALUE.toLong()).length shouldBeLessThanOrEqual NICKNAME_COLUMN_LENGTH
            }
        }
    }
})

private const val NICKNAME_COLUMN_LENGTH = 10
