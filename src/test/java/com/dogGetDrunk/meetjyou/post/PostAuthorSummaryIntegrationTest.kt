package com.dogGetDrunk.meetjyou.post

import com.dogGetDrunk.meetjyou.auth.jwt.JwtProvider
import com.dogGetDrunk.meetjyou.chat.room.ChatRoomRepository
import com.dogGetDrunk.meetjyou.common.idempotency.IdempotencyKeyRepository
import com.dogGetDrunk.meetjyou.party.PartyRepository
import com.dogGetDrunk.meetjyou.post.dto.CreatePostRequest
import com.dogGetDrunk.meetjyou.post.dto.CreatePostResponse
import com.dogGetDrunk.meetjyou.post.dto.PostAuthorSummary
import com.dogGetDrunk.meetjyou.user.AuthProvider
import com.dogGetDrunk.meetjyou.user.Role
import com.dogGetDrunk.meetjyou.user.User
import com.dogGetDrunk.meetjyou.user.UserRepository
import com.dogGetDrunk.meetjyou.userparty.UserPartyRepository
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.firebase.FirebaseApp
import com.oracle.bmc.auth.AuthenticationDetailsProvider
import com.oracle.bmc.objectstorage.ObjectStorageClient
import com.oracle.bmc.workrequests.WorkRequestClient
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import java.time.Instant
import java.util.UUID

/**
 * Post responses embed the author summary (#194) through the real HTTP chain and H2, and a withdrawn
 * author stays hidden exactly as the basic-info 404 hides them.
 */
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("test")
@MockBean(
    FirebaseApp::class,
    AuthenticationDetailsProvider::class,
    ObjectStorageClient::class,
    WorkRequestClient::class,
)
class PostAuthorSummaryIntegrationTest : BehaviorSpec() {

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var jwtProvider: JwtProvider

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var postRepository: PostRepository

    @Autowired
    private lateinit var partyRepository: PartyRepository

    @Autowired
    private lateinit var chatRoomRepository: ChatRoomRepository

    @Autowired
    private lateinit var userPartyRepository: UserPartyRepository

    @Autowired
    private lateinit var idempotencyKeyRepository: IdempotencyKeyRepository

    @Value("\${local.server.port}")
    private var port: Int = 0

    private var userSeq = 0

    private fun url(path: String) = "http://localhost:$port/api/v1$path"

    private fun saveUser(hasProfileImage: Boolean = false): User {
        userSeq++
        val user = User(
            email = null,
            nickname = "pa$userSeq",
            authProvider = AuthProvider.KAKAO,
            externalId = "pa-ext-$userSeq",
        )
        user.hasProfileImage = hasProfileImage
        return userRepository.save(user)
    }

    private fun tokenOf(user: User) = jwtProvider.generateAccessToken(user.uuid, Role.USER)

    private fun createPost(author: User): UUID {
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            setBearerAuth(tokenOf(author))
        }
        val request = CreatePostRequest(
            title = "title",
            content = "content",
            isInstant = false,
            itinStart = Instant.now().plusSeconds(ITIN_START_OFFSET_SECONDS),
            itinFinish = Instant.now().plusSeconds(ITIN_FINISH_OFFSET_SECONDS),
            location = "Seoul",
            capacity = CAPACITY,
            companionSpec = null,
            planUuid = null,
            isPlanPublic = null,
        )
        val response = restTemplate.postForEntity(url("/posts"), HttpEntity(request, headers), String::class.java)
        response.statusCode.value() shouldBe HttpStatus.OK.value()
        return objectMapper.readValue(response.body, CreatePostResponse::class.java).uuid
    }

    private fun getJson(path: String, viewer: User): JsonNode {
        val headers = HttpHeaders().apply { setBearerAuth(tokenOf(viewer)) }
        val response = restTemplate.exchange(url(path), HttpMethod.GET, HttpEntity<Void>(headers), String::class.java)
        response.statusCode.value() shouldBe HttpStatus.OK.value()
        return objectMapper.readTree(response.body)
    }

    private fun authorsByPost(page: JsonNode): Map<String, JsonNode> =
        page.get("content").associate { it.get("uuid").asText() to it.get("author") }

    private fun withdraw(user: User) {
        user.withdraw(Instant.now())
        userRepository.save(user)
    }

    init {
        extensions(SpringExtension())

        afterEach {
            idempotencyKeyRepository.deleteAll()
            postRepository.deleteAll()
            userPartyRepository.deleteAll()
            chatRoomRepository.deleteAll()
            partyRepository.deleteAll()
            userRepository.deleteAll()
        }

        given("게시글 목록을 조회할 때") {
            `when`("정상 작성자와 탈퇴 유예 중인 작성자의 글이 섞여 있으면") {
                then("정상 작성자는 닉네임·프로필 유무를 담고, 탈퇴 작성자는 가려서 탈퇴로 표시한다") {
                    val viewer = saveUser()
                    val active = saveUser(hasProfileImage = true)
                    val withdrawn = saveUser(hasProfileImage = true)
                    val activePost = createPost(active)
                    val withdrawnPost = createPost(withdrawn)
                    withdraw(withdrawn)

                    val authors = authorsByPost(getJson("/posts", viewer))

                    authors.getValue(activePost.toString()).let {
                        it.get("uuid").asText() shouldBe active.uuid.toString()
                        it.get("nickname").asText() shouldBe active.nickname
                        it.get("hasProfileImage").asBoolean() shouldBe true
                        it.get("isWithdrawn").asBoolean() shouldBe false
                    }
                    authors.getValue(withdrawnPost.toString()).let {
                        it.get("uuid").asText() shouldBe withdrawn.uuid.toString()
                        it.get("nickname").asText() shouldBe PostAuthorSummary.WITHDRAWN_NICKNAME
                        it.get("hasProfileImage").asBoolean() shouldBe false
                        it.get("isWithdrawn").asBoolean() shouldBe true
                    }
                }
            }
        }

        given("작성자별 게시글 목록과 게시글 상세를 조회할 때") {
            `when`("작성자가 탈퇴 유예 중이면") {
                then("두 응답 모두 작성자를 가려서 탈퇴로 표시한다") {
                    val viewer = saveUser()
                    val withdrawn = saveUser(hasProfileImage = true)
                    val postUuid = createPost(withdrawn)
                    withdraw(withdrawn)

                    val byAuthor = getJson("/posts/author/${withdrawn.uuid}", viewer)
                        .get("content").single().get("author")
                    val detail = getJson("/posts/$postUuid", viewer).get("author")

                    for (author in listOf(byAuthor, detail)) {
                        author.get("nickname").asText() shouldBe PostAuthorSummary.WITHDRAWN_NICKNAME
                        author.get("hasProfileImage").asBoolean() shouldBe false
                        author.get("isWithdrawn").asBoolean() shouldBe true
                    }
                }
            }
        }
    }

    companion object {
        private const val ITIN_START_OFFSET_SECONDS = 3_600L
        private const val ITIN_FINISH_OFFSET_SECONDS = 7_200L
        private const val CAPACITY = 4
    }
}
