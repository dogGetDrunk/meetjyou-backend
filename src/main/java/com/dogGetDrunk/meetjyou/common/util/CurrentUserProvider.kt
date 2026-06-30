package com.dogGetDrunk.meetjyou.common.util

import com.dogGetDrunk.meetjyou.common.exception.business.notFound.UserNotFoundException
import com.dogGetDrunk.meetjyou.user.User
import com.dogGetDrunk.meetjyou.user.UserRepository
import org.springframework.stereotype.Component
import org.springframework.web.context.annotation.RequestScope
import java.util.UUID

@Component
@RequestScope
class CurrentUserProvider(private val userRepository: UserRepository) {
    val uuid: UUID by lazy { SecurityUtil.getCurrentUserUuid() }
    val user: User by lazy {
        userRepository.findByUuid(uuid) ?: throw UserNotFoundException(uuid)
    }
}
