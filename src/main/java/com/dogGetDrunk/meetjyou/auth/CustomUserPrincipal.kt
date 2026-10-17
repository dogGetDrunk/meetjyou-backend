package com.dogGetDrunk.meetjyou.auth

import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.userdetails.UserDetails
import java.util.UUID

data class CustomUserPrincipal(
    val uuid: UUID,
    private val authorities: Collection<GrantedAuthority> = emptyList()
) : UserDetails {

    override fun getUsername(): String = uuid.toString()

    override fun getAuthorities(): Collection<GrantedAuthority> = authorities

    override fun getPassword(): String? = null

    override fun isAccountNonExpired(): Boolean = true
    override fun isAccountNonLocked(): Boolean = true
    override fun isCredentialsNonExpired(): Boolean = true
    override fun isEnabled(): Boolean = true
}
