package com.dogGetDrunk.meetjyou.notification.sender

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class FirebaseConfig {

    @Bean
    fun firebaseApp(): FirebaseApp {
        val options = buildFirebaseOptions(GoogleCredentials.getApplicationDefault())

        return if (FirebaseApp.getApps().isEmpty()) {
            FirebaseApp.initializeApp(options)
        } else {
            FirebaseApp.getInstance()
        }
    }

    // The SDK default of 0 means no timeout, so a hung FCM call would block the dispatcher thread.
    fun buildFirebaseOptions(credentials: GoogleCredentials): FirebaseOptions =
        FirebaseOptions.builder()
            .setCredentials(credentials)
            .setConnectTimeout(CONNECT_TIMEOUT_MS)
            .setReadTimeout(READ_TIMEOUT_MS)
            .setWriteTimeout(WRITE_TIMEOUT_MS)
            .build()

    private companion object {
        const val CONNECT_TIMEOUT_MS = 10_000
        const val READ_TIMEOUT_MS = 10_000
        const val WRITE_TIMEOUT_MS = 10_000
    }
}
