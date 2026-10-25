package com.dogGetDrunk.meetjyou.auth.social.apple

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.http.HttpRequest
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.http.client.ClientHttpResponse
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient

/**
 * Sign in with Apple REST API calls used to revoke a user's authorization (#171).
 * https://developer.apple.com/documentation/sign_in_with_apple/sign_in_with_apple_rest_api
 */
class AppleAuthClient(
    private val props: AppleOidcProperties,
    private val clientSecretFactory: AppleClientSecretFactory,
    private val restClient: RestClient,
) {

    /** Trades a one-time authorization code (valid ~5 minutes) for the user's tokens. */
    fun exchangeCode(authorizationCode: String): AppleTokenResponse {
        val form = baseForm().apply {
            add("code", authorizationCode)
            add("grant_type", GRANT_TYPE_AUTHORIZATION_CODE)
        }
        return post(TOKEN_URI, form, AppleTokenResponse::class.java)
            ?: throw AppleAuthApiException(AppleAuthApiException.Reason.UNAVAILABLE, "Empty token response")
    }

    fun revoke(token: String, tokenTypeHint: String) {
        val form = baseForm().apply {
            add("token", token)
            add("token_type_hint", tokenTypeHint)
        }
        post(REVOKE_URI, form, Void::class.java)
    }

    private fun baseForm(): MultiValueMap<String, String> = LinkedMultiValueMap<String, String>().apply {
        add("client_id", props.clientId)
        add("client_secret", clientSecretFactory.create())
    }

    // ResourceAccessException = Apple unreachable (timeout, connection refused).
    private fun <T : Any> post(uri: String, form: MultiValueMap<String, String>, type: Class<T>): T? = try {
        restClient.post()
            .uri(uri)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(form)
            .retrieve()
            .onStatus(HttpStatusCode::isError, ::translateError)
            .toEntity(type)
            .body
    } catch (e: ResourceAccessException) {
        throw AppleAuthApiException(AppleAuthApiException.Reason.UNAVAILABLE, "Apple unreachable: $uri", e)
    }

    private fun translateError(request: HttpRequest, response: ClientHttpResponse) {
        val status = response.statusCode
        val reason = if (status.is4xxClientError) {
            AppleAuthApiException.Reason.REJECTED
        } else {
            AppleAuthApiException.Reason.UNAVAILABLE
        }
        // Apple's error body is a short code such as {"error":"invalid_grant"}; no user data in it.
        val body = response.body.readNBytes(MAX_ERROR_BODY_BYTES).toString(Charsets.UTF_8)
        throw AppleAuthApiException(reason, "Apple responded ${status.value()} for ${request.uri.path}: $body")
    }

    companion object {
        const val TOKEN_URI = "https://appleid.apple.com/auth/token"
        const val REVOKE_URI = "https://appleid.apple.com/auth/revoke"
        const val TOKEN_TYPE_HINT_REFRESH = "refresh_token"
        const val TOKEN_TYPE_HINT_ACCESS = "access_token"
        private const val GRANT_TYPE_AUTHORIZATION_CODE = "authorization_code"
        private const val MAX_ERROR_BODY_BYTES = 256
    }
}

// token_type / expires_in are not needed for revocation.
@JsonIgnoreProperties(ignoreUnknown = true)
data class AppleTokenResponse(
    @JsonProperty("access_token") val accessToken: String,
    @JsonProperty("refresh_token") val refreshToken: String? = null,
    @JsonProperty("id_token") val idToken: String,
)

class AppleAuthApiException(
    val reason: Reason,
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause) {
    enum class Reason { REJECTED, UNAVAILABLE }
}
