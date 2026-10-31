package com.dogGetDrunk.meetjyou.party

import com.dogGetDrunk.meetjyou.common.exception.business.party.PartyJoinRequestNotFoundException
import com.dogGetDrunk.meetjyou.common.util.CurrentUserProvider
import com.dogGetDrunk.meetjyou.notification.outbox.NotificationOutboxRepository
import com.dogGetDrunk.meetjyou.user.AuthProvider
import com.dogGetDrunk.meetjyou.user.User
import com.dogGetDrunk.meetjyou.user.UserRepository
import com.dogGetDrunk.meetjyou.userparty.MemberStatus
import com.dogGetDrunk.meetjyou.userparty.PartyRole
import com.dogGetDrunk.meetjyou.userparty.UserParty
import com.dogGetDrunk.meetjyou.userparty.UserPartyRepository
import com.google.firebase.FirebaseApp
import com.oracle.bmc.auth.AuthenticationDetailsProvider
import com.oracle.bmc.objectstorage.ObjectStorageClient
import com.oracle.bmc.workrequests.WorkRequestClient
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Guards issue #222: membership state must survive concurrent writers.
 * - An unlocked writer (read cursor) must not revert member_status it never changed (@DynamicUpdate).
 * - approve must decide on the committed membership, not a copy read before the lock: a
 *   double-clicked approve, a racing reject, or a racing cancel must not double-count party.joined
 *   or resurrect the request.
 *
 * Races are reproduced deterministically: the "concurrent" write runs in a REQUIRES_NEW transaction
 * (its own persistence context and connection) after the outer transaction already loaded the row.
 */
@SpringBootTest
@ActiveProfiles("test")
@MockBean(
    FirebaseApp::class,
    AuthenticationDetailsProvider::class,
    ObjectStorageClient::class,
    WorkRequestClient::class,
)
class UserPartyLostUpdateIntegrationTest : BehaviorSpec() {

    @MockBean
    private lateinit var currentUserProvider: CurrentUserProvider

    @Autowired
    private lateinit var partyService: PartyService

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var partyRepository: PartyRepository

    @Autowired
    private lateinit var userPartyRepository: UserPartyRepository

    @Autowired
    private lateinit var notificationOutboxRepository: NotificationOutboxRepository

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    private data class Fixture(val partyUuid: UUID, val host: User, val applicant: User)

    private fun createUser(prefix: String): User {
        val email = "$prefix-${System.nanoTime()}@example.com"
        return userRepository.save(
            User(
                email = email,
                nickname = email.substringBefore("@"),
                authProvider = AuthProvider.KAKAO,
                externalId = email,
            )
        )
    }

    private fun createPartyWithApplicant(applicantStatus: MemberStatus): Fixture {
        val host = createUser("host")
        val applicant = createUser("applicant")
        val partyUuid = inTransaction {
            val now = Instant.now()
            val party = partyRepository.save(
                Party(
                    itinStart = now.plus(1, ChronoUnit.DAYS),
                    itinFinish = now.plus(TRIP_DAYS, ChronoUnit.DAYS),
                    destination = "Busan",
                    joined = INITIAL_JOINED,
                    capacity = CAPACITY,
                    name = "Party",
                )
            )
            userPartyRepository.save(UserParty(party, host, PartyRole.HOST))
            val membership = UserParty(party, applicant, PartyRole.MEMBER)
            if (applicantStatus == MemberStatus.PENDING) membership.pending()
            userPartyRepository.save(membership)
            party.uuid
        }
        return Fixture(partyUuid, host, applicant)
    }

    private fun actAs(user: User) {
        Mockito.`when`(currentUserProvider.uuid).thenReturn(user.uuid)
    }

    private fun preload(fixture: Fixture): UserParty =
        userPartyRepository.findByParty_UuidAndUser_Uuid(fixture.partyUuid, fixture.applicant.uuid)
            ?: error("membership missing")

    private fun storedMembership(fixture: Fixture): UserParty? =
        userPartyRepository.findByParty_UuidAndUser_Uuid(fixture.partyUuid, fixture.applicant.uuid)

    private fun storedJoined(fixture: Fixture): Int =
        partyRepository.findByUuid(fixture.partyUuid)?.joined ?: error("party missing")

    private fun <T> inTransaction(block: () -> T): T =
        TransactionTemplate(transactionManager).execute { block() } ?: error("Empty transaction result")

    private fun inNewTransaction(block: () -> Unit) {
        val template = TransactionTemplate(transactionManager).apply {
            propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
        }
        template.executeWithoutResult { block() }
    }

    private fun cleanup() = inTransaction {
        notificationOutboxRepository.deleteAll()
        userPartyRepository.deleteAll()
        partyRepository.deleteAll()
        userRepository.deleteAll()
    }

    init {
        extensions(SpringExtension())

        afterEach { cleanup() }

        given("잠금 없이 멤버십을 읽어 둔 트랜잭션이 있을 때") {
            `when`("그 사이 다른 트랜잭션이 강퇴를 커밋하고, 원 트랜잭션은 읽음 커서만 바꿔 커밋하면") {
                then("강퇴 상태는 덮어써지지 않고 읽음 커서도 남는다") {
                    val fixture = createPartyWithApplicant(MemberStatus.JOINED)

                    inTransaction {
                        val stale = preload(fixture)
                        inNewTransaction { preload(fixture).ban() }
                        stale.updateLastReadMessageId(LAST_READ_MESSAGE_ID)
                    }

                    val stored = storedMembership(fixture) ?: error("membership missing")
                    stored.memberStatus shouldBe MemberStatus.BANNED
                    stored.lastReadMessageId shouldBe LAST_READ_MESSAGE_ID
                }
            }
        }

        given("승인 요청이 신청 행을 먼저 읽어 둔 상태일 때") {
            `when`("그 사이 같은 신청의 승인이 먼저 커밋되면(승인 연타)") {
                then("두 번째 승인은 재시도로 보고 인원 수를 한 번만 올린다") {
                    val fixture = createPartyWithApplicant(MemberStatus.PENDING)
                    actAs(fixture.host)

                    inTransaction {
                        preload(fixture)
                        inNewTransaction { partyService.approveJoinRequest(fixture.partyUuid, fixture.applicant.uuid) }
                        partyService.approveJoinRequest(fixture.partyUuid, fixture.applicant.uuid)
                    }

                    storedMembership(fixture)?.memberStatus shouldBe MemberStatus.JOINED
                    storedJoined(fixture) shouldBe INITIAL_JOINED + 1
                }
            }

            `when`("그 사이 거절이 먼저 커밋되면") {
                then("승인은 신청 없음으로 거절되고 거절 상태와 인원 수가 그대로 남는다") {
                    val fixture = createPartyWithApplicant(MemberStatus.PENDING)
                    actAs(fixture.host)

                    shouldThrow<PartyJoinRequestNotFoundException> {
                        inTransaction {
                            preload(fixture)
                            inNewTransaction {
                                partyService.rejectJoinRequest(fixture.partyUuid, fixture.applicant.uuid)
                            }
                            partyService.approveJoinRequest(fixture.partyUuid, fixture.applicant.uuid)
                        }
                    }

                    storedMembership(fixture)?.memberStatus shouldBe MemberStatus.REJECTED
                    storedJoined(fixture) shouldBe INITIAL_JOINED
                }
            }

            `when`("그 사이 신청자가 신청 취소를 먼저 커밋하면") {
                then("승인은 신청 없음으로 거절되고 멤버십이 되살아나지 않는다") {
                    // Pins behavior only: H2's READ COMMITTED already hides the deleted row from a
                    // plain re-query. The REPEATABLE READ case is UserPartyLockFreshReadDockerTest.
                    val fixture = createPartyWithApplicant(MemberStatus.PENDING)

                    shouldThrow<PartyJoinRequestNotFoundException> {
                        inTransaction {
                            preload(fixture)
                            actAs(fixture.applicant)
                            inNewTransaction { partyService.cancelJoinRequest(fixture.partyUuid) }
                            actAs(fixture.host)
                            partyService.approveJoinRequest(fixture.partyUuid, fixture.applicant.uuid)
                        }
                    }

                    storedMembership(fixture) shouldBe null
                    storedJoined(fixture) shouldBe INITIAL_JOINED
                }
            }
        }
    }

    companion object {
        private const val INITIAL_JOINED = 1
        private const val CAPACITY = 4
        private const val TRIP_DAYS = 3L
        private const val LAST_READ_MESSAGE_ID = 42L
    }
}
