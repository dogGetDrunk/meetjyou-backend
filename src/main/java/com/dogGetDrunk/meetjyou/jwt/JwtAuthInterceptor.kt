package com.dogGetDrunk.meetjyou.jwt

import com.dogGetDrunk.meetjyou.common.exception.business.jwt.MissingAuthorizationHeaderException
import com.dogGetDrunk.meetjyou.common.exception.business.notFound.UserNotFoundException
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

        UserContext.setUser(user) // ✅ UserContext에 저장
        return true
    }

    override fun afterCompletion(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
        ex: Exception?,
    ) {
        UserContext.clear() // ✅ 요청 종료 시 반드시 정리
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
