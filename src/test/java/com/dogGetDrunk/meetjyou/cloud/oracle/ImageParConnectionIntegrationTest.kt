package com.dogGetDrunk.meetjyou.cloud.oracle

import com.dogGetDrunk.meetjyou.auth.jwt.JwtProvider
import com.dogGetDrunk.meetjyou.image.ImageTarget
import com.dogGetDrunk.meetjyou.notificationcenter.support.NotificationCenterFixtures
import com.dogGetDrunk.meetjyou.party.Party
import com.dogGetDrunk.meetjyou.party.PartyImageState
import com.dogGetDrunk.meetjyou.party.PartyRepository
import com.dogGetDrunk.meetjyou.post.Post
import com.dogGetDrunk.meetjyou.post.PostRepository
import com.dogGetDrunk.meetjyou.support.EXTERNAL_CALL_H2_URL
import com.dogGetDrunk.meetjyou.support.ExternalCallTestConfig
import com.dogGetDrunk.meetjyou.user.AuthProvider
import com.dogGetDrunk.meetjyou.user.User
import com.dogGetDrunk.meetjyou.user.UserRepository
import com.dogGetDrunk.meetjyou.userparty.UserPartyRepository
import com.google.firebase.FirebaseApp
import com.oracle.bmc.auth.AuthenticationDetailsProvider
import com.oracle.bmc.objectstorage.ObjectStorageClient
import com.oracle.bmc.objectstorage.model.PreauthenticatedRequest
import com.oracle.bmc.objectstorage.requests.CreatePreauthenticatedRequestRequest
import com.oracle.bmc.objectstorage.requests.DeleteObjectRequest
import com.oracle.bmc.objectstorage.responses.CreatePreauthenticatedRequestResponse
import com.oracle.bmc.objectstorage.responses.DeleteObjectResponse
import com.oracle.bmc.workrequests.WorkRequestClient
import com.zaxxer.hikari.HikariDataSource
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.context.annotation.Import
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.util.UUID
import javax.sql.DataSource

/**
 * AT-006: every OCI image route that reads the DB first makes each OCI call with no transaction and no
 * pooled connection held (open-in-view excluded, DB work finished first). Every OCI call is recorded,
 * and each case checks the exact operations and objects before asserting on them, so a case that never
 * reaches OCI cannot pass.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["spring.datasource.url=$EXTERNAL_CALL_H2_URL"],
)
@ActiveProfiles("test")
@Import(ExternalCallTestConfig::class)
@MockBean(
    FirebaseApp::class, AuthenticationDetailsProvider::class, ObjectStorageClient::class, WorkRequestClient::class,
)
class ImageParConnectionIntegrationTest : BehaviorSpec() {
    @Autowired private lateinit var rest: TestRestTemplate
    @Autowired private lateinit var jwt: JwtProvider
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var parties: PartyRepository
    @Autowired private lateinit var posts: PostRepository
    @Autowired private lateinit var memberships: UserPartyRepository
    @Autowired private lateinit var dataSource: DataSource

    @Autowired private lateinit var objectStorage: ObjectStorageClient

    private data class OciCall(
        val operation: String,
        val objectName: String,
        val transactionActive: Boolean,
        val activeConnections: Int,
    )

    private data class Scene(val user: User, val party: Party, val post: Post)

    private val calls = mutableListOf<OciCall>()

    private val pool by lazy { dataSource.unwrap(HikariDataSource::class.java) }

    private fun record(operation: String, objectName: String) {
        calls += OciCall(
            operation,
            objectName,
            TransactionSynchronizationManager.isActualTransactionActive(),
            pool.hikariPoolMXBean.activeConnections,
        )
    }

    private fun stubObjectStorage() {
        Mockito.doAnswer { invocation ->
            val request = invocation.getArgument<CreatePreauthenticatedRequestRequest>(0)
            record(PAR, request.createPreauthenticatedRequestDetails.objectName)
            val par = PreauthenticatedRequest.builder().accessUri("/p/${UUID.randomUUID()}").build()
            CreatePreauthenticatedRequestResponse.builder().preauthenticatedRequest(par).build()
        }.`when`(objectStorage).createPreauthenticatedRequest(ArgumentMatchers.any())
        Mockito.doAnswer { invocation ->
            record(DELETE, invocation.getArgument<DeleteObjectRequest>(0).objectName)
            DeleteObjectResponse.builder().build()
        }.`when`(objectStorage).deleteObject(ArgumentMatchers.any())
    }

    // Fresh uuids per case keep the download PAR cache from answering instead of OCI.
    private fun scene(state: PartyImageState = PartyImageState.CUSTOM): Scene {
        val tag = UUID.randomUUID().toString().take(NICKNAME_LENGTH)
        val user = users.save(User(null, tag, AuthProvider.KAKAO, tag))
        val party = NotificationCenterFixtures.party()
        party.imageState = state
        parties.save(party)
        val post = posts.save(NotificationCenterFixtures.post(party, user))
        memberships.save(NotificationCenterFixtures.hostUserParty(party, user))
        return Scene(user, party, post)
    }

    private fun call(user: User, method: HttpMethod, path: String, body: Any? = null): ResponseEntity<String> {
        val headers = HttpHeaders()
        headers.setBearerAuth(jwt.generateAccessToken(user.uuid, user.role))
        headers.contentType = MediaType.APPLICATION_JSON
        return rest.exchange("/api/v1$path", method, HttpEntity(body, headers), String::class.java)
    }

    private fun assertOciCalls(vararg expected: Pair<String, String>) {
        calls.map { it.operation to it.objectName } shouldContainExactly expected.toList()
        calls.forEach {
            it.transactionActive shouldBe false
            it.activeConnections shouldBe 0
        }
    }

    init {
        extensions(SpringExtension())

        beforeEach {
            calls.clear()
            stubObjectStorage()
        }

        given("업로드 PAR") {
            then("모집글: 작성자 확인 뒤 원본과 썸네일 PAR을 커넥션 없이 발급") {
                val s = scene()
                call(s.user, HttpMethod.POST, "/posts/${s.post.uuid}/img/par/upload").statusCode shouldBe HttpStatus.OK
                assertOciCalls(
                    PAR to ImageTarget.POST_ORIGINAL.toObjectName(s.post.uuid),
                    PAR to ImageTarget.POST_THUMBNAIL.toObjectName(s.post.uuid),
                )
            }
            then("파티: 호스트 확인 뒤 원본과 썸네일 PAR을 커넥션 없이 발급") {
                val s = scene()
                call(s.user, HttpMethod.POST, "/parties/${s.party.uuid}/img/par/upload")
                    .statusCode shouldBe HttpStatus.OK
                assertOciCalls(
                    PAR to ImageTarget.PARTY_ORIGINAL.toObjectName(s.party.uuid),
                    PAR to ImageTarget.PARTY_THUMBNAIL.toObjectName(s.party.uuid),
                )
            }
        }

        given("파티 원본 다운로드 PAR") {
            then("CUSTOM이면 파티 원본 PAR") {
                val s = scene(PartyImageState.CUSTOM)
                call(s.user, HttpMethod.POST, "/parties/${s.party.uuid}/img/original/par/download")
                    .statusCode shouldBe HttpStatus.OK
                assertOciCalls(PAR to ImageTarget.PARTY_ORIGINAL.toObjectName(s.party.uuid))
            }
            then("INHERITED이면 모집글 원본 PAR") {
                val s = scene(PartyImageState.INHERITED)
                call(s.user, HttpMethod.POST, "/parties/${s.party.uuid}/img/original/par/download")
                    .statusCode shouldBe HttpStatus.OK
                assertOciCalls(PAR to ImageTarget.POST_ORIGINAL.toObjectName(s.post.uuid))
            }
        }

        given("파티 썸네일 일괄 다운로드 PAR") {
            then("CUSTOM과 INHERITED를 섞어도 호출마다 커넥션 없음") {
                val custom = scene(PartyImageState.CUSTOM)
                val inherited = scene(PartyImageState.INHERITED)
                val body = mapOf("uuid" to listOf(custom.party.uuid, inherited.party.uuid))
                call(custom.user, HttpMethod.POST, "/parties/img/thumbnail/par/download", body)
                    .statusCode shouldBe HttpStatus.OK
                assertOciCalls(
                    PAR to ImageTarget.PARTY_THUMBNAIL.toObjectName(custom.party.uuid),
                    PAR to ImageTarget.POST_THUMBNAIL.toObjectName(inherited.post.uuid),
                )
            }
        }

        given("이미지 삭제") {
            then("모집글: 작성자 확인 뒤 원본과 썸네일 삭제") {
                val s = scene()
                call(s.user, HttpMethod.DELETE, "/posts/${s.post.uuid}/img").statusCode shouldBe HttpStatus.NO_CONTENT
                assertOciCalls(
                    DELETE to ImageTarget.POST_ORIGINAL.toObjectName(s.post.uuid),
                    DELETE to ImageTarget.POST_THUMBNAIL.toObjectName(s.post.uuid),
                )
            }
            then("파티: 호스트 확인 뒤 원본과 썸네일 삭제") {
                val s = scene()
                call(s.user, HttpMethod.DELETE, "/parties/${s.party.uuid}/img")
                    .statusCode shouldBe HttpStatus.NO_CONTENT
                assertOciCalls(
                    DELETE to ImageTarget.PARTY_ORIGINAL.toObjectName(s.party.uuid),
                    DELETE to ImageTarget.PARTY_THUMBNAIL.toObjectName(s.party.uuid),
                )
            }
            then("프로필: 원본과 썸네일 삭제") {
                val s = scene()
                call(s.user, HttpMethod.DELETE, "/users/me/img/profile").statusCode shouldBe HttpStatus.NO_CONTENT
                assertOciCalls(
                    DELETE to ImageTarget.USER_PROFILE_ORIGINAL.toObjectName(s.user.uuid),
                    DELETE to ImageTarget.USER_PROFILE_THUMBNAIL.toObjectName(s.user.uuid),
                )
            }
        }
    }

    private companion object {
        const val NICKNAME_LENGTH = 10
        const val PAR = "par"
        const val DELETE = "delete"
    }
}
