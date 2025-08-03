package com.dogGetDrunk.meetjyou.common.util

import com.dogGetDrunk.meetjyou.auth.CustomUserPrincipal
import org.springframework.security.core.context.SecurityContextHolder
import java.util.UUID

object SecurityUtil {

    fun getCurrentUserUuid(): UUID {
        val principal = getPrincipal()
        return principal.uuid
    }

    fun getCurrentUserEmail(): String {
        val principal = getPrincipal()
        return principal.username
    }

    private fun getPrincipal(): CustomUserPrincipal {
        val authentication = SecurityContextHolder.getContext().authentication
            ?: throw IllegalStateException("인증 정보가 없습니다.")

        return authentication.principal as? CustomUserPrincipal
            ?: throw IllegalStateException("인증 주체가 CustomUserPrincipal이 아닙니다.")
    }
}
