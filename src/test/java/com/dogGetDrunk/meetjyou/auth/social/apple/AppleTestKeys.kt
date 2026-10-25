package com.dogGetDrunk.meetjyou.auth.social.apple

import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import java.util.Base64

/** A throwaway P-256 key in the same PKCS#8 PEM shape as an Apple .p8 file. */
object AppleTestKeys {
    const val TEAM_ID = "TEAMID1234"
    const val KEY_ID = "KEYID56789"
    const val CLIENT_ID = "com.example.app"

    val keyPair: KeyPair = KeyPairGenerator.getInstance("EC").apply {
        initialize(ECGenParameterSpec("secp256r1"))
    }.generateKeyPair()

    private val body: String = Base64.getEncoder().encodeToString(keyPair.private.encoded)

    /** As Apple ships it: header, 64-char lines, footer. */
    val p8Pem: String = listOf(
        "-----BEGIN PRIVATE KEY-----",
        *body.chunked(PEM_LINE_LENGTH).toTypedArray(),
        "-----END PRIVATE KEY-----",
    ).joinToString("\n")

    /** As stored in the deploy .env: base64 body on a single line. */
    val singleLineBody: String = body

    fun props(privateKey: String = p8Pem) = AppleOidcProperties(
        issuer = "https://appleid.apple.com",
        jwksUri = "https://appleid.apple.com/auth/keys",
        clientId = CLIENT_ID,
        clockSkewSeconds = 60,
        jwkCacheTtlMinutes = 60,
        teamId = TEAM_ID,
        keyId = KEY_ID,
        privateKey = privateKey,
    )

    fun disabledProps() = props().copy(teamId = "", keyId = "", privateKey = "")
}

private const val PEM_LINE_LENGTH = 64
