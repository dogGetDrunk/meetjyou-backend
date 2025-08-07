package com.dogGetDrunk.meetjyou.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.core.userdetails.User
import org.springframework.security.provisioning.InMemoryUserDetailsManager

@Configuration
class SwaggerUserConfig {

    @Bean
    fun inMemoryUserDetailsManager(
        @Value("\${spring.security.user.name}") username: String,
        @Value("\${spring.security.user.password}") password: String
    ): InMemoryUserDetailsManager {
        val user = User
            .withUsername(username)
            .password("{noop}$password") // `{noop}`: 암호 인코딩 없이 사용
            .roles("ADMIN")
            .build()
        return InMemoryUserDetailsManager(user)
    }
}
