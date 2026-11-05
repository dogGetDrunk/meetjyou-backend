package com.dogGetDrunk.meetjyou.common.idempotency

import com.dogGetDrunk.meetjyou.auth.jwt.JwtProvider
import com.dogGetDrunk.meetjyou.common.exception.ErrorResponse
import com.dogGetDrunk.meetjyou.plan.PlanRepository
import com.dogGetDrunk.meetjyou.plan.PlanService
import com.dogGetDrunk.meetjyou.plan.dto.CreatePlanRequest
import com.dogGetDrunk.meetjyou.post.PostRepository
import com.dogGetDrunk.meetjyou.post.PostService
import com.dogGetDrunk.meetjyou.post.dto.CreatePostRequest
import com.dogGetDrunk.meetjyou.user.AuthProvider
import com.dogGetDrunk.meetjyou.user.Role
import com.dogGetDrunk.meetjyou.user.User
import com.dogGetDrunk.meetjyou.user.UserRepository
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.firebase.FirebaseApp
import com.oracle.bmc.auth.AuthenticationDetailsProvider
import com.oracle.bmc.objectstorage.ObjectStorageClient
import com.oracle.bmc.workrequests.WorkRequestClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.reset
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * AT-003-3: a DataIntegrityViolationException that is NOT an idempotency-key race must surface as
 * 500 INTERNAL_SERVER_ERROR through the real controllers, resolveAfterConflict, key service and
 * GlobalExceptionHandler. Only the create boundary (PostService.createPost / PlanService.createPlan)
 * is spied to inject the unmapped constraint violation; no key row exists for the request.
 */
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("test")
@MockBean(
    FirebaseApp::class,
    AuthenticationDetailsProvider::class,
    ObjectStorageClient::class,
    WorkRequestClient::class,
)
class NonConflictIntegrityIntegrationTest {

    @Autowired private lateinit var restTemplate: TestRestTemplate
    @Autowired private lateinit var jwtProvider: JwtProvider
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var posts: PostRepository
    @Autowired private lateinit var plans: PlanRepository
    @Autowired private lateinit var keys: IdempotencyKeyRepository
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var transactionManager: PlatformTransactionManager
    @SpyBean private lateinit var postService: PostService
    @SpyBean private lateinit var planService: PlanService

    @Value("\${local.server.port}")
    private var port: Int = 0

    private val createdUsers = mutableListOf<User>()

    private data class Snapshot(val posts: Long, val plans: Long, val keys: Long)

    @AfterEach
    fun cleanUp() {
        reset(postService, planService)
        // Bulk @Modifying delete needs a transaction; the test method itself runs without one.
        TransactionTemplate(transactionManager).executeWithoutResult {
            keys.deleteAllByUserIdIn(createdUsers.map { it.id })
        }
        users.deleteAll(createdUsers)
        createdUsers.clear()
    }

    @Test
    fun `AT-003-3 post create with unmapped integrity violation and no key row returns 500 not 404`() {
        val token = tokenOfNewUser()
        doThrow(DataIntegrityViolationException(INJECTED_MESSAGE))
            .`when`(postService).createPost(anyValue(), anyValue())
        val before = snapshot()

        val response = send("/api/v1/posts", postRequest(), token)

        assertInternalServerError(response.statusCode.value(), response.body)
        assertEquals(before, snapshot())
    }

    @Test
    fun `AT-003-3 plan create with unmapped integrity violation and no key row returns 500 not 404`() {
        val token = tokenOfNewUser()
        doThrow(DataIntegrityViolationException(INJECTED_MESSAGE))
            .`when`(planService).createPlan(anyValue(), anyValue())
        val before = snapshot()

        val response = send("/api/v1/plans", planRequest(), token)

        assertInternalServerError(response.statusCode.value(), response.body)
        assertEquals(before, snapshot())
    }

    private fun assertInternalServerError(status: Int, body: String?) {
        assertNotEquals(HttpStatus.NOT_FOUND.value(), status)
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), status)
        val error = objectMapper.readValue(body, ErrorResponse::class.java)
        assertEquals("INTERNAL_SERVER_ERROR", error.errorCode)
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), error.status)
    }

    private fun snapshot() = Snapshot(posts.count(), plans.count(), keys.count())

    private fun send(path: String, body: Any, token: String) =
        restTemplate.postForEntity(
            "http://localhost:$port$path",
            HttpEntity(body, headers(token)),
            String::class.java,
        )

    private fun headers(token: String) = HttpHeaders().apply {
        contentType = MediaType.APPLICATION_JSON
        setBearerAuth(token)
        set(IDEMPOTENCY_HEADER, "non-conflict-${UUID.randomUUID()}")
    }

    private fun tokenOfNewUser(): String {
        val suffix = UUID.randomUUID().toString()
        val user = users.save(
            User(
                email = "$suffix@example.com",
                nickname = suffix.take(NICKNAME_LENGTH),
                authProvider = AuthProvider.KAKAO,
                externalId = suffix,
            ),
        )
        createdUsers.add(user)
        return jwtProvider.generateAccessToken(user.uuid, Role.USER)
    }

    private fun postRequest(): CreatePostRequest {
        val start = Instant.now().plus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MILLIS)
        return CreatePostRequest(
            title = "Trip",
            content = "content",
            isInstant = false,
            itinStart = start,
            itinFinish = start.plus(1, ChronoUnit.HOURS),
            location = "Seoul",
            capacity = 4,
            companionSpec = null,
            planUuid = null,
            isPlanPublic = null,
        )
    }

    private fun planRequest(): CreatePlanRequest {
        val start = Instant.now().plus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MILLIS)
        return CreatePlanRequest(
            title = "Plan",
            itinStart = start,
            itinFinish = start.plus(1, ChronoUnit.HOURS),
            location = "Seoul",
            centerLat = 37.5,
            centerLng = 127.0,
            memo = null,
            markers = emptyList(),
        )
    }

    // Matcher that satisfies Kotlin's non-null parameter types while stubbing the spy.
    @Suppress("UNCHECKED_CAST")
    private fun <T> anyValue(): T {
        ArgumentMatchers.any<T>()
        return null as T
    }

    private companion object {
        const val IDEMPOTENCY_HEADER = "Idempotency-Key"
        const val INJECTED_MESSAGE = "injected unmapped integrity violation"
        const val NICKNAME_LENGTH = 10
    }
}
