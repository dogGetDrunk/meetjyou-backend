package com.dogGetDrunk.meetjyou.party

import jakarta.persistence.EntityManager
import jakarta.persistence.LockModeType
import java.util.UUID

/** Party row lock that returns the committed row whether or not the party is already loaded (#172). */
interface PartyRowLock {
    /**
     * Locks the party row (`SELECT ... FOR UPDATE`) and returns it with its committed state.
     * Discards unflushed changes on that party, so call it before modifying the party.
     */
    fun lockByUuid(uuid: UUID): Party?
}

class PartyRowLockImpl(private val entityManager: EntityManager) : PartyRowLock {

    override fun lockByUuid(uuid: UUID): Party? {
        val party = entityManager.createQuery(LOCK_QUERY, Party::class.java)
            .setParameter(UUID_PARAM, uuid)
            .setLockMode(LockModeType.PESSIMISTIC_WRITE)
            .resultList
            .firstOrNull()
            ?: return null
        // A locked query hands back the already-managed instance untouched if the party was loaded
        // earlier in this transaction. Re-read it under the lock: a locking read sees the latest
        // committed row, while a plain refresh would still see this transaction's old snapshot.
        entityManager.refresh(party, LockModeType.PESSIMISTIC_WRITE)
        return party
    }

    private companion object {
        const val UUID_PARAM = "uuid"
        const val LOCK_QUERY = "SELECT p FROM Party p WHERE p.uuid = :$UUID_PARAM"
    }
}
