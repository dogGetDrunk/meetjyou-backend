package com.dogGetDrunk.meetjyou.user

import com.dogGetDrunk.meetjyou.auth.social.IdToken
import com.dogGetDrunk.meetjyou.auth.social.SocialPrincipal
import com.dogGetDrunk.meetjyou.auth.social.SocialVerifier
import com.dogGetDrunk.meetjyou.auth.social.SocialVerifierRegistry
import com.dogGetDrunk.meetjyou.common.exception.business.notFound.SocialAccountNotLinkedException
import com.dogGetDrunk.meetjyou.common.exception.business.user.LastSocialAccountUnlinkException
import com.dogGetDrunk.meetjyou.common.exception.business.user.SocialAccountAlreadyLinkedException
import com.dogGetDrunk.meetjyou.common.exception.business.user.SocialProviderAlreadyLinkedException
import com.dogGetDrunk.meetjyou.common.util.CurrentUserProvider
import com.dogGetDrunk.meetjyou.user.dto.LinkSocialAccountRequest
import com.dogGetDrunk.meetjyou.user.support.UserFixtures
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.springframework.dao.DataIntegrityViolationException

class SocialAccountServiceTest : BehaviorSpec() {

    private val socialVerifierRegistry = mockk<SocialVerifierRegistry>()
    private val socialVerifier = mockk<SocialVerifier>()
    private val userRepository = mockk<UserRepository>()
    private val currentUserProvider = mockk<CurrentUserProvider>()
    private val sut = SocialAccountService(socialVerifierRegistry, userRepository, currentUserProvider)

    override fun isolationMode() = IsolationMode.InstancePerLeaf

    private fun userWith(vararg providers: AuthProvider): User {
        val user = UserFixtures.user()
        providers.forEach { user.addSocialAccount(it, "$it-sub", "${it.name.lowercase()}@example.com") }
        every { currentUserProvider.user } returns user
        return user
    }

    private fun stubVerifiedGoogle(subject: String = GOOGLE_SUB) {
        every { socialVerifierRegistry.get(AuthProvider.GOOGLE) } returns socialVerifier
        every { socialVerifier.verifyAndExtract(IdToken(ID_TOKEN), NONCE) } returns
            SocialPrincipal(AuthProvider.GOOGLE, subject, GOOGLE_EMAIL)
    }

    private fun linkGoogle() = sut.link(LinkSocialAccountRequest(AuthProvider.GOOGLE, idToken = ID_TOKEN), NONCE)

    companion object {
        private const val ID_TOKEN = "id-token"
        private const val NONCE = "nonce"
        private const val GOOGLE_SUB = "google-sub"
        private const val GOOGLE_EMAIL = "linked@gmail.com"
        private const val UNIQUE_VIOLATION = "uk_social_account_provider_external"
    }

    init {
        beforeEach { clearAllMocks() }

        given("카카오만 연결된 유저가") {
            `when`("아무에게도 연결되지 않은 구글 계정을 연결하면") {
                then("새 계정을 추가하고 created=true를 돌려준다") {
                    val user = userWith(AuthProvider.KAKAO)
                    stubVerifiedGoogle()
                    every { userRepository.findBySocialAccount(AuthProvider.GOOGLE, GOOGLE_SUB) } returns null
                    every { userRepository.flush() } just runs

                    val result = linkGoogle()

                    result.created shouldBe true
                    result.account.email shouldBe GOOGLE_EMAIL
                    user.socialAccounts.map { it.provider } shouldContainExactly
                        listOf(AuthProvider.KAKAO, AuthProvider.GOOGLE)
                    verify(exactly = 1) { userRepository.flush() }
                }
            }

            `when`("다른 유저에게 연결된 구글 계정을 연결하면") {
                then("SocialAccountAlreadyLinkedException을 던지고 아무것도 추가하지 않는다") {
                    val user = userWith(AuthProvider.KAKAO)
                    stubVerifiedGoogle()
                    val otherUser = UserFixtures.user(
                        email = UserFixtures.email(2),
                        nickname = UserFixtures.nickname(2),
                    )
                    every { userRepository.findBySocialAccount(AuthProvider.GOOGLE, GOOGLE_SUB) } returns otherUser

                    shouldThrow<SocialAccountAlreadyLinkedException> { linkGoogle() }
                    user.socialAccounts.map { it.provider } shouldContainExactly listOf(AuthProvider.KAKAO)
                }
            }

            `when`("동시 요청에 밀려 flush에서 unique 제약 위반이 나면") {
                then("500 대신 409용 SocialAccountAlreadyLinkedException으로 바꾼다") {
                    userWith(AuthProvider.KAKAO)
                    stubVerifiedGoogle()
                    every { userRepository.findBySocialAccount(AuthProvider.GOOGLE, GOOGLE_SUB) } returns null
                    every { userRepository.flush() } throws DataIntegrityViolationException(UNIQUE_VIOLATION)

                    shouldThrow<SocialAccountAlreadyLinkedException> { linkGoogle() }
                }
            }
        }

        given("구글 계정이 이미 연결된 유저가") {
            `when`("같은 구글 계정 연결을 다시 요청하면") {
                then("추가하지 않고 기존 계정을 created=false로 돌려준다") {
                    val user = userWith(AuthProvider.KAKAO)
                    user.addSocialAccount(AuthProvider.GOOGLE, GOOGLE_SUB, GOOGLE_EMAIL)
                    stubVerifiedGoogle()
                    every { userRepository.findBySocialAccount(AuthProvider.GOOGLE, GOOGLE_SUB) } returns user

                    val result = linkGoogle()

                    result.created shouldBe false
                    user.socialAccounts.size shouldBe 2
                    verify(exactly = 0) { userRepository.flush() }
                }
            }

            `when`("다른 구글 계정을 연결하면") {
                then("SocialProviderAlreadyLinkedException을 던진다") {
                    val user = userWith(AuthProvider.KAKAO)
                    user.addSocialAccount(AuthProvider.GOOGLE, GOOGLE_SUB, GOOGLE_EMAIL)
                    stubVerifiedGoogle(subject = "another-google-sub")
                    every { userRepository.findBySocialAccount(AuthProvider.GOOGLE, "another-google-sub") } returns null

                    shouldThrow<SocialProviderAlreadyLinkedException> { linkGoogle() }
                    user.socialAccounts.size shouldBe 2
                }
            }
        }

        given("카카오·구글이 연결된 유저가") {
            `when`("구글을 해제하면") {
                then("잠금 조회한 행 기준으로 구글만 제거한다") {
                    val user = userWith(AuthProvider.KAKAO, AuthProvider.GOOGLE)
                    every { userRepository.findSocialAccountsForUpdate(user.id) } returns user.socialAccounts.toList()

                    sut.unlink(AuthProvider.GOOGLE)

                    user.socialAccounts.map { it.provider } shouldContainExactly listOf(AuthProvider.KAKAO)
                }
            }

            `when`("연결하지 않은 애플을 해제하면") {
                then("SocialAccountNotLinkedException을 던진다") {
                    val user = userWith(AuthProvider.KAKAO, AuthProvider.GOOGLE)
                    every { userRepository.findSocialAccountsForUpdate(user.id) } returns user.socialAccounts.toList()

                    shouldThrow<SocialAccountNotLinkedException> { sut.unlink(AuthProvider.APPLE) }
                }
            }

            // The in-memory collection is stale: a concurrent unlink already removed Google.
            `when`("메모리의 컬렉션은 2개지만 잠금 조회 결과가 1개면") {
                then("잠금 조회 결과를 믿고 마지막 계정 해제를 거부한다") {
                    val user = userWith(AuthProvider.KAKAO, AuthProvider.GOOGLE)
                    val lockedRows = user.socialAccounts.filter { it.provider == AuthProvider.KAKAO }
                    every { userRepository.findSocialAccountsForUpdate(user.id) } returns lockedRows

                    shouldThrow<LastSocialAccountUnlinkException> { sut.unlink(AuthProvider.KAKAO) }
                    user.socialAccounts.size shouldBe 2
                }
            }
        }

        given("소셜 계정이 카카오 하나뿐인 유저가") {
            `when`("카카오를 해제하면") {
                then("LastSocialAccountUnlinkException을 던지고 계정을 남긴다") {
                    val user = userWith(AuthProvider.KAKAO)
                    every { userRepository.findSocialAccountsForUpdate(user.id) } returns user.socialAccounts.toList()

                    shouldThrow<LastSocialAccountUnlinkException> { sut.unlink(AuthProvider.KAKAO) }
                    user.socialAccounts.size shouldBe 1
                }
            }
        }

        given("여러 계정이 연결된 유저가") {
            `when`("목록을 조회하면") {
                then("연결한 순서대로 돌려준다") {
                    userWith(AuthProvider.KAKAO, AuthProvider.GOOGLE)

                    sut.getMySocialAccounts().map { it.provider } shouldContainExactly
                        listOf(AuthProvider.KAKAO, AuthProvider.GOOGLE)
                }
            }
        }
    }
}
