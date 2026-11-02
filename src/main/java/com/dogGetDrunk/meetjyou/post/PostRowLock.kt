package com.dogGetDrunk.meetjyou.post

import jakarta.persistence.EntityManager
import jakarta.persistence.LockModeType
import java.util.UUID

/** Post row lock that returns the committed row whether or not the post is already loaded. */
interface PostRowLock {
    /**
     * Locks the post row (`SELECT ... FOR UPDATE`) and returns it with its committed state.
     * Discards unflushed changes on that post, so call it before modifying the post.
     * Lock the linked party first (party -> post) to keep the aggregate lock order.
     */
    fun lockByUuid(uuid: UUID): Post?
}

class PostRowLockImpl(private val entityManager: EntityManager) : PostRowLock {

    override fun lockByUuid(uuid: UUID): Post? {
        val post = entityManager.createQuery(LOCK_QUERY, Post::class.java)
            .setParameter(UUID_PARAM, uuid)
            .setLockMode(LockModeType.PESSIMISTIC_WRITE)
            .resultList
            .firstOrNull()
            ?: return null
        // Same reason as PartyRowLockImpl: an already-managed post comes back untouched from the
        // locked query, so re-read it under the lock to see the latest committed row.
        entityManager.refresh(post, LockModeType.PESSIMISTIC_WRITE)
        return post
    }

    private companion object {
        const val UUID_PARAM = "uuid"
        const val LOCK_QUERY = "SELECT p FROM Post p WHERE p.uuid = :$UUID_PARAM"
    }
}
