package com.dogGetDrunk.meetjyou.notification.outbox

import com.dogGetDrunk.meetjyou.notification.NotificationType
import com.dogGetDrunk.meetjyou.user.AuthProvider
import com.dogGetDrunk.meetjyou.user.User
import com.dogGetDrunk.meetjyou.user.UserRepository
import com.google.firebase.FirebaseApp
import com.oracle.bmc.auth.AuthenticationDetailsProvider
import com.oracle.bmc.objectstorage.ObjectStorageClient
import com.oracle.bmc.workrequests.WorkRequestClient
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

/**
 * Guards #293's blind spot: H2 builds the schema from the entity, so without an explicit length the
 * column was 255 there while production's V1 has VARCHAR(100), and a 115-char key passed every H2 test.
 */
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("test")
@MockBean(
    FirebaseApp::class,
    AuthenticationDetailsProvider::class,
    ObjectStorageClient::class,
    WorkRequestClient::class,
    SimpMessagingTemplate::class,
)
class NotificationOutboxDedupKeyLengthTest : BehaviorSpec() {

    @Autowired
    private lateinit var outboxRepository: NotificationOutboxRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    init {
        extensions(SpringExtension())

        afterEach {
            outboxRepository.deleteAll()
            userRepository.deleteAll()
        }

        given("AT-002-1: H2 테스트 스키마의 notification_outbox") {
            `when`("dedup_key가 100자를 넘으면") {
                then("운영 MySQL처럼 저장이 거절된다") {
                    shouldThrow<DataIntegrityViolationException> { saveWithKey(MAX_LENGTH + 1) }
                }
            }
            `when`("dedup_key가 정확히 100자면") {
                then("저장된다") {
                    shouldNotThrowAny { saveWithKey(MAX_LENGTH) }
                }
            }
        }
    }

    private fun saveWithKey(length: Int) {
        TransactionTemplate(transactionManager).executeWithoutResult {
            val user = userRepository.save(User("len@example.com", "lenTester", AuthProvider.KAKAO, "ext-len"))
            outboxRepository.saveAndFlush(
                NotificationOutbox(
                    user = user,
                    type = NotificationType.CHAT_MESSAGE,
                    dataJson = "{}",
                    dedupKey = "k".repeat(length),
                )
            )
        }
    }

    companion object {
        private const val MAX_LENGTH = 100
    }
}
