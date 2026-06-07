package com.dogGetDrunk.meetjyou.config

import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class ApiVersionConfig : WebMvcConfigurer {

    companion object {
        const val V1 = "/api/v1"
    }

    override fun configurePathMatch(configurer: PathMatchConfigurer) {
        configurer.addPathPrefix(V1) { it.isAnnotationPresent(RestControllerV1::class.java) }
    }
}
