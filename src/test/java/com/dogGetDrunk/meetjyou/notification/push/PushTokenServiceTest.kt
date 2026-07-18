package com.dogGetDrunk.meetjyou.notification.push

import com.dogGetDrunk.meetjyou.common.util.CurrentUserProvider
import com.dogGetDrunk.meetjyou.notification.push.dto.RegisterPushTokenRequest
import com.dogGetDrunk.meetjyou.user.support.UserFixtures
import com.dogGetDrunk.meetjyou.version.AppVersion
import com.dogGetDrunk.meetjyou.version.AppVersionRepository
import com.dogGetDrunk.meetjyou.version.Platform
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk

class PushTokenServiceTest : BehaviorSpec() {

    private val pushTokenRepository = mockk<PushTokenRepository>(relaxed = true)
    private val appVersionRepository = mockk<AppVersionRepository>(relaxed = true)
    private val currentUserProvider = mockk<CurrentUserProvider>(relaxed = true)
    private val sut = PushTokenService(pushTokenRepository, appVersionRepository, currentUserProvider)

    override fun isolationMode() = IsolationMode.InstancePerLeaf

    init {
        beforeEach { clearAllMocks() }

        given("register 호출 시") {
            val appVersion = AppVersion(platform = Platform.ANDROID, version = "1.0.0", forceUpdate = false)
            val request = RegisterPushTokenRequest(
                token = "fcm-token",
                platform = "ANDROID",
                appVersion = "1.0.0",
                deviceModel = "Pixel 9",
            )

            `when`("다른 계정이 등록했던 토큰을 새 계정이 다시 등록하면") {
                then("토큰 소유자가 현재 유저로 재할당된다") {
                    val previousOwner = UserFixtures.user()
                    val newOwner = UserFixtures.user(email = "new@test.com", nickname = "newbie", externalId = "ext-new")
                    val existing = PushToken(
                        token = "fcm-token",
                        platform = PushToken.PushPlatform.ANDROID,
                        deviceModel = "Pixel 8",
                        active = false,
                        user = previousOwner,
                        appVersion = appVersion,
                    )

                    every { currentUserProvider.user } returns newOwner
                    every { appVersionRepository.findByVersionAndPlatform("1.0.0", Platform.ANDROID) } returns appVersion
                    every { pushTokenRepository.findByToken("fcm-token") } returns existing
                    every { pushTokenRepository.save(any()) } returnsArgument 0

                    sut.register(request)

                    existing.user shouldBe newOwner
                    existing.active shouldBe true
                    existing.deviceModel shouldBe "Pixel 9"
                }
            }
        }
    }
}
