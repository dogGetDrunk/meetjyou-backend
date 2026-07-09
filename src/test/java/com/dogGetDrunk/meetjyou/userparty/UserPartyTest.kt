package com.dogGetDrunk.meetjyou.userparty

import com.dogGetDrunk.meetjyou.party.Party
import com.dogGetDrunk.meetjyou.user.User
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk

class UserPartyTest : BehaviorSpec() {

    override fun isolationMode() = IsolationMode.InstancePerLeaf

    private fun newUserParty(role: PartyRole = PartyRole.MEMBER): UserParty =
        UserParty(party = mockk<Party>(relaxed = true), user = mockk<User>(relaxed = true), role = role)

    init {
        given("새로 생성된 UserParty는") {
            `when`("기본 상태를 확인하면") {
                then("JOINED 상태이고 두 읽음 플래그 모두 true다") {
                    val userParty = newUserParty()

                    userParty.memberStatus shouldBe MemberStatus.JOINED
                    userParty.hostRead shouldBe true
                    userParty.applicantRead shouldBe true
                    userParty.isActiveMember() shouldBe true
                }
            }
        }

        given("가입 신청 처리 흐름에서") {
            `when`("pending()을 호출하면") {
                then("PENDING 상태가 되고 호스트의 안읽음 플래그가 켜진다") {
                    val userParty = newUserParty()

                    userParty.pending()

                    userParty.memberStatus shouldBe MemberStatus.PENDING
                    userParty.hostRead shouldBe false
                    userParty.isActiveMember() shouldBe false
                }
            }

            `when`("approve()를 호출하면") {
                then("JOINED 상태가 되고 신청자의 안읽음 플래그가 켜진다") {
                    val userParty = newUserParty()
                    userParty.pending()

                    userParty.approve()

                    userParty.memberStatus shouldBe MemberStatus.JOINED
                    userParty.applicantRead shouldBe false
                    userParty.isActiveMember() shouldBe true
                }
            }

            `when`("reject()를 호출하면") {
                then("REJECTED 상태가 되고 신청자의 안읽음 플래그가 켜진다") {
                    val userParty = newUserParty()
                    userParty.pending()

                    userParty.reject()

                    userParty.memberStatus shouldBe MemberStatus.REJECTED
                    userParty.applicantRead shouldBe false
                    userParty.isActiveMember() shouldBe false
                }
            }
        }

        given("멤버 상태 변경 흐름에서") {
            `when`("ban()을 호출하면") {
                then("BANNED 상태가 된다") {
                    val userParty = newUserParty()

                    userParty.ban()

                    userParty.memberStatus shouldBe MemberStatus.BANNED
                    userParty.isActiveMember() shouldBe false
                }
            }

            `when`("leave()를 호출하면") {
                then("LEFT 상태가 된다") {
                    val userParty = newUserParty()

                    userParty.leave()

                    userParty.memberStatus shouldBe MemberStatus.LEFT
                    userParty.isActiveMember() shouldBe false
                }
            }
        }

        given("읽음 처리 플래그를 초기화할 때") {
            `when`("markHostRead()를 호출하면") {
                then("호스트 안읽음 플래그가 꺼진다") {
                    val userParty = newUserParty()
                    userParty.pending()

                    userParty.markHostRead()

                    userParty.hostRead shouldBe true
                }
            }

            `when`("markApplicantRead()를 호출하면") {
                then("신청자 안읽음 플래그가 꺼진다") {
                    val userParty = newUserParty()
                    userParty.approve()

                    userParty.markApplicantRead()

                    userParty.applicantRead shouldBe true
                }
            }
        }

        given("마지막으로 읽은 메시지 id를 갱신할 때") {
            `when`("아직 값이 없으면") {
                then("전달된 값으로 설정된다") {
                    val userParty = newUserParty()

                    userParty.updateLastReadMessageId(5L)

                    userParty.lastReadMessageId shouldBe 5L
                }
            }

            `when`("더 큰 값이 들어오면") {
                then("갱신된다") {
                    val userParty = newUserParty()
                    userParty.updateLastReadMessageId(5L)

                    userParty.updateLastReadMessageId(10L)

                    userParty.lastReadMessageId shouldBe 10L
                }
            }

            `when`("더 작거나 같은 값이 들어오면") {
                then("무시하고 기존 값을 유지한다") {
                    val userParty = newUserParty()
                    userParty.updateLastReadMessageId(10L)

                    userParty.updateLastReadMessageId(3L)
                    userParty.updateLastReadMessageId(10L)

                    userParty.lastReadMessageId shouldBe 10L
                }
            }
        }
    }
}
