package com.dogGetDrunk.meetjyou.user.support

import com.dogGetDrunk.meetjyou.auth.jwt.JwtProvider
import com.dogGetDrunk.meetjyou.preference.PreferenceRepository
import com.dogGetDrunk.meetjyou.preference.UserPreferenceRepository
import com.dogGetDrunk.meetjyou.user.UserRepository
import com.dogGetDrunk.meetjyou.user.UserService
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.clearAllMocks
import io.mockk.mockk
import io.mockk.unmockkAll

abstract class UserTestBase : BehaviorSpec() {
    protected lateinit var userRepository: UserRepository
    protected lateinit var preferenceRepository: PreferenceRepository
    protected lateinit var userPreferenceRepository: UserPreferenceRepository
    protected lateinit var jwtProvider: JwtProvider
    protected lateinit var userService: UserService

    override fun isolationMode(): IsolationMode = IsolationMode.InstancePerLeaf
    init {
        beforeTest {
            userRepository = mockk(relaxed = true)
            preferenceRepository = mockk(relaxed = true)
            userPreferenceRepository = mockk(relaxed = true)
            jwtProvider = mockk(relaxed = true)
            userService = UserService(
                userRepository,
                preferenceRepository,
                userPreferenceRepository,
                jwtProvider,
            )
        }
        afterTest {
            clearAllMocks()
        }
        afterSpec {
            unmockkAll()
        }
    }
}
