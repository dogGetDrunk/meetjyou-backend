package com.dogGetDrunk.meetjyou.user

import com.dogGetDrunk.meetjyou.auth.social.apple.AppleAuthorizationRevoker
import com.dogGetDrunk.meetjyou.auth.social.apple.AppleAuthorizationRevoker.Trigger
import com.dogGetDrunk.meetjyou.common.exception.business.user.LastSocialAccountUnlinkException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder

class AccountExitServiceTest : BehaviorSpec() {

    private lateinit var userService: UserService
    private lateinit var socialAccountService: SocialAccountService
    private lateinit var revoker: AppleAuthorizationRevoker
    private lateinit var sut: AccountExitService

    override fun isolationMode() = IsolationMode.InstancePerLeaf

    init {
        beforeEach {
            userService = mockk()
            socialAccountService = mockk()
            revoker = mockk(relaxed = true)
            sut = AccountExitService(userService, socialAccountService, revoker)
        }

        given("탈퇴 시") {
            `when`("애플 계정이 연결된 회원이면") {
                then("탈퇴를 먼저 커밋한 뒤 그 애플 sub와 code로 연동을 해제한다") {
                    every { userService.withdrawUser() } returns "apple-sub"

                    sut.withdraw("code-1")

                    verifyOrder {
                        userService.withdrawUser()
                        revoker.revokeQuietly("code-1", "apple-sub", Trigger.WITHDRAWAL)
                    }
                }
            }

            `when`("탈퇴 자체가 실패하면") {
                then("애플 연동은 건드리지 않는다") {
                    every { userService.withdrawUser() } throws IllegalStateException("db down")

                    shouldThrow<IllegalStateException> { sut.withdraw("code-1") }

                    verify(exactly = 0) { revoker.revokeQuietly(any(), any(), any()) }
                }
            }
        }

        given("소셜 계정 연결 해제 시") {
            `when`("APPLE을 해제하면") {
                then("해제된 애플 sub와 code로 연동을 해제한다") {
                    every { socialAccountService.unlink(AuthProvider.APPLE) } returns "apple-sub"

                    sut.unlinkSocialAccount(AuthProvider.APPLE, "code-1")

                    verify { revoker.revokeQuietly("code-1", "apple-sub", Trigger.UNLINK) }
                }
            }

            `when`("APPLE이 아닌 provider를 해제하면") {
                then("애플 연동 해제를 시도하지 않는다") {
                    every { socialAccountService.unlink(AuthProvider.KAKAO) } returns "kakao-sub"

                    sut.unlinkSocialAccount(AuthProvider.KAKAO, "code-1")

                    verify(exactly = 0) { revoker.revokeQuietly(any(), any(), any()) }
                }
            }

            `when`("마지막 계정이라 해제가 거절되면 (409)") {
                then("요청이 실패했으니 애플 연동도 해제하지 않는다") {
                    every { socialAccountService.unlink(AuthProvider.APPLE) } throws
                        LastSocialAccountUnlinkException(AuthProvider.APPLE)

                    shouldThrow<LastSocialAccountUnlinkException> {
                        sut.unlinkSocialAccount(AuthProvider.APPLE, "code-1")
                    }

                    verify(exactly = 0) { revoker.revokeQuietly(any(), any(), any()) }
                }
            }
        }
    }
}
