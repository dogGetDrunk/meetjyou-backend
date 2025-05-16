package com.dogGetDrunk.meetjyou.jwt

import com.dogGetDrunk.meetjyou.common.exception.business.jwt.InvalidAccessTokenException
import com.dogGetDrunk.meetjyou.common.exception.business.jwt.MissingAuthorizationHeaderException
import com.dogGetDrunk.meetjyou.common.exception.business.notFound.UserNotFoundException
import com.dogGetDrunk.meetjyou.user.User
import com.dogGetDrunk.meetjyou.user.UserRepository
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor

@Component
class JwtAuthInterceptor(
    private val jwtManager: JwtManager,
    private val userRepository: UserRepository,
) : HandlerInterceptor {

    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
    ): Boolean {
        val token = resolveToken(request)
            ?: throw MissingAuthorizationHeaderException(request.getHeader("Authorization"))

        val uuid = jwtManager.extractUuid(token)

        val user = userRepository.findByUuid(uuid)
            ?: throw UserNotFoundException(uuid)

        request.setAttribute("authenticatedUser", user)
        return true
    }

    private fun resolveToken(request: HttpServletRequest): String? {
        val bearer = request.getHeader("Authorization")
        return if (bearer?.startsWith("Bearer ") == true) {
            bearer.substring("Bearer ".length)
        } else {
            null
        }
    }
}
