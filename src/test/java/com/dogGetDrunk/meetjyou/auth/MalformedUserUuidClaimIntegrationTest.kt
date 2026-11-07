package com.dogGetDrunk.meetjyou.auth

import com.dogGetDrunk.meetjyou.auth.jwt.JwtProvider
import com.dogGetDrunk.meetjyou.common.exception.ErrorCode
import com.dogGetDrunk.meetjyou.common.exception.ErrorResponse
import com.dogGetDrunk.meetjyou.preference.Age
import com.dogGetDrunk.meetjyou.preference.Gender
import com.dogGetDrunk.meetjyou.preference.Personality
import com.dogGetDrunk.meetjyou.preference.Preference
import com.dogGetDrunk.meetjyou.preference.PreferenceRepository
import com.dogGetDrunk.meetjyou.preference.PreferenceType
import com.dogGetDrunk.meetjyou.preference.UserPreference
import com.dogGetDrunk.meetjyou.preference.UserPreferenceRepository
import com.dogGetDrunk.meetjyou.user.AuthProvider
import com.dogGetDrunk.meetjyou.user.Role
import com.dogGetDrunk.meetjyou.user.User
import com.dogGetDrunk.meetjyou.user.UserRepository
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.firebase.FirebaseApp
import com.oracle.bmc.auth.AuthenticationDetailsProvider
import com.oracle.bmc.objectstorage.ObjectStorageClient
import com.oracle.bmc.workrequests.WorkRequestClient
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.io.Decoders
import io.jsonwebtoken.security.Keys
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
import org.springframework.http.ResponseEntity
import org.springframework.test.context.ActiveProfiles
import java.util.Date

/**
 * AT-002-2: a validly signed access token whose `userUuid` claim is not a UUID must be rejected
 * with 401 INVALID_JWT on a private API (base behavior: IllegalArgumentException -> 500).
 */
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("test")
@MockBean(
    FirebaseApp::class,
    AuthenticationDetailsProvider::class,
    ObjectStorageClient::class,
    WorkRequestClient::class,
)
class MalformedUserUuidClaimIntegrationTest : BehaviorSpec() {

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var jwtProvider: JwtProvider

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var userPreferenceRepository: UserPreferenceRepository

    @Autowired
    private lateinit var preferenceRepository: PreferenceRepository

    @Value("\${local.server.port}")
    private var port: Int = 0

    @Value("\${jwt.secret-key}")
    private lateinit var secretKey: String

    @Value("\${jwt.issuer}")
    private lateinit var issuer: String

    private fun getProfile(bearer: String): ResponseEntity<String> {
        val headers = HttpHeaders().apply { setBearerAuth(bearer) }
        return restTemplate.exchange(
            "http://localhost:$port/api/v1/users/me/profile",
            HttpMethod.GET,
            HttpEntity<Void>(headers),
            String::class.java,
        )
    }

    // Signed with the real key, access type, future expiry: only the userUuid claim is wrong.
    private fun accessTokenWithUserUuid(userUuid: String): String {
        val now = Date()
        return Jwts.builder()
            .issuer(issuer)
            .subject(userUuid)
            .claim(USER_UUID_CLAIM, userUuid)
            .claim("role", Role.USER.name)
            .claim("token_type", "access")
            .issuedAt(now)
            .expiration(Date(now.time + TTL_MILLIS))
            .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(secretKey)), Jwts.SIG.HS256)
            .compact()
    }

    private fun saveNormalUser(): User {
        val user = userRepository.save(
            User(email = null, nickname = "muuid", authProvider = AuthProvider.KAKAO, externalId = "muuid-ext"),
        )
        userPreferenceRepository.saveAll(preferenceRepository.findAll().map { UserPreference(user, it) })
        return user
    }

    init {
        extensions(SpringExtension())

        beforeEach {
            preferenceRepository.saveAll(
                listOf(
                    Preference(PreferenceType.GENDER, Gender.M.name),
                    Preference(PreferenceType.AGE, Age.TWENTY.name),
                    Preference(PreferenceType.PERSONALITY, Personality.INTROVERTED.name),
                ),
            )
        }
        afterEach {
            userPreferenceRepository.deleteAll()
            userRepository.deleteAll()
            preferenceRepository.deleteAll()
        }

        given("AT-002-2: userUuid 클레임이 UUID 형식이 아닌 access token으로") {
            `when`("비공개 GET /users/me/profile을 호출하면") {
                then("500이 아니라 401 INVALID_JWT를 받는다") {
                    val response = getProfile(accessTokenWithUserUuid(MALFORMED_UUID))

                    response.statusCode.value() shouldBe HttpStatus.UNAUTHORIZED.value()
                    objectMapper.readValue(response.body, ErrorResponse::class.java).errorCode shouldBe
                        ErrorCode.INVALID_JWT.name
                }
            }
        }

        given("AT-002-2 대조군: 정상 access token(NORMAL 유저)으로") {
            `when`("같은 비공개 API를 호출하면") {
                then("200을 받는다(동작 유지)") {
                    val user = saveNormalUser()

                    val response = getProfile(jwtProvider.generateAccessToken(user.uuid, Role.USER))

                    response.statusCode.value() shouldBe HttpStatus.OK.value()
                }
            }
        }
    }

    private companion object {
        const val MALFORMED_UUID = "not-a-uuid"
        const val USER_UUID_CLAIM = "userUuid"
        const val TTL_MILLIS = 600_000L
    }
}
