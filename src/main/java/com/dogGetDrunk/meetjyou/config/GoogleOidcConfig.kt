package com.dogGetDrunk.meetjyou.config

import com.dogGetDrunk.meetjyou.auth.social.google.GoogleOidcProperties
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class GoogleOidcConfig(
    private val props: GoogleOidcProperties
) {

    @Bean
    fun googleIdTokenVerifier(): GoogleIdTokenVerifier {
        val transport = NetHttpTransport()
        val jsonFactory = GsonFactory.getDefaultInstance()

        return GoogleIdTokenVerifier.Builder(transport, jsonFactory)
            .setAudience(props.clientIds)
            .build()
    }
}
