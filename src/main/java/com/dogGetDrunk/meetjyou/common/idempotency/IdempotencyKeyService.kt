package com.dogGetDrunk.meetjyou.common.idempotency

import com.dogGetDrunk.meetjyou.common.exception.business.idempotency.IdempotencyKeyConflictException
import com.dogGetDrunk.meetjyou.user.User
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.util.UUID

@Service
class IdempotencyKeyService(
    private val idempotencyKeyRepository: IdempotencyKeyRepository,
    private val objectMapper: ObjectMapper,
) {
    fun hashRequest(request: Any): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(objectMapper.writeValueAsBytes(request))
        return digest.joinToString("") { "%02x".format(it) }
    }

    @Transactional(readOnly = true)
    fun resolveExisting(scope: IdempotencyScope, user: User, key: String, requestHash: String): UUID? {
        val existing = idempotencyKeyRepository.findByScopeAndUser_IdAndIdempotencyKey(scope, user.id, key)
            ?: return null
        if (existing.requestHash != requestHash) throw IdempotencyKeyConflictException(key)
        return existing.resourceUuid
    }

    // No @Transactional here on purpose - this must always join the caller's transaction
    // (PostService.createPost / PlanService.createPlan), so the idempotency row is committed
    // atomically with the resource it points at. Never catch the unique-constraint violation
    // here: Hibernate marks the persistence context rollback-only after a failed flush, so
    // swallowing it mid-transaction would leave the session unusable. Let it propagate to the
    // controller, which is the only place a self-invocation-safe recovery can happen.
    fun record(scope: IdempotencyScope, user: User, key: String, resourceUuid: UUID, requestHash: String) {
        idempotencyKeyRepository.save(
            IdempotencyKey(
                scope = scope,
                user = user,
                idempotencyKey = key,
                resourceUuid = resourceUuid,
                requestHash = requestHash,
            )
        )
    }
}
