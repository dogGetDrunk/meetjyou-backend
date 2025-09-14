package com.dogGetDrunk.meetjyou.user.support

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.clearAllMocks
import io.mockk.unmockkAll

abstract class UserTestBase : BehaviorSpec() {
    override fun isolationMode(): IsolationMode = IsolationMode.InstancePerLeaf
    init {
        afterTest { clearAllMocks() }
        afterSpec { unmockkAll() }
    }
}
