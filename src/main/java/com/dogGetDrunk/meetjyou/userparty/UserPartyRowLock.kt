package com.dogGetDrunk.meetjyou.userparty

import jakarta.persistence.EntityManager
import jakarta.persistence.LockModeType
import java.util.UUID

/** Membership row lock that returns the committed row whether or not it is already loaded (#222). */
interface UserPartyRowLock {
    /**
     * Locks the membership row (`SELECT ... FOR UPDATE`) and returns it with its committed state,
     * or null if the row no longer exists (e.g. a concurrent cancel deleted it).
     * Discards unflushed changes on that membership, so call it before modifying it.
     */
    fun lockByPartyUuidAndUserUuid(partyUuid: UUID, userUuid: UUID): UserParty?
}

class UserPartyRowLockImpl(private val entityManager: EntityManager) : UserPartyRowLock {

    override fun lockByPartyUuidAndUserUuid(partyUuid: UUID, userUuid: UUID): UserParty? {
        val membership = entityManager.createQuery(LOCK_QUERY, UserParty::class.java)
            .setParameter(PARTY_UUID_PARAM, partyUuid)
            .setParameter(USER_UUID_PARAM, userUuid)
            .setLockMode(LockModeType.PESSIMISTIC_WRITE)
            .resultList
            .firstOrNull()
            ?: return null
        // Same reason as PartyRowLockImpl: a locked query returns an already-managed instance as is,
        // and a plain refresh would re-read this transaction's REPEATABLE READ snapshot.
        entityManager.refresh(membership, LockModeType.PESSIMISTIC_WRITE)
        return membership
    }

    private companion object {
        const val PARTY_UUID_PARAM = "partyUuid"
        const val USER_UUID_PARAM = "userUuid"
        const val LOCK_QUERY =
            "SELECT up FROM UserParty up WHERE up.party.uuid = :$PARTY_UUID_PARAM AND up.user.uuid = :$USER_UUID_PARAM"
    }
}
