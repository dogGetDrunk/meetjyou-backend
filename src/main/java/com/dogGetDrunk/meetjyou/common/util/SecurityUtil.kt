package com.dogGetDrunk.meetjyou.common.util

import com.dogGetDrunk.meetjyou.auth.CustomUserPrincipal
import com.dogGetDrunk.meetjyou.common.exception.business.auth.UnauthenticatedException
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
            ?: throw UnauthenticatedException("current-user")

        return authentication.principal as? CustomUserPrincipal
            ?: throw UnauthenticatedException("current-user")
    }
}
