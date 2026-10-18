package com.dogGetDrunk.meetjyou.user

import org.hibernate.exception.ConstraintViolationException
import org.springframework.dao.DataIntegrityViolationException

/** Unique constraint names the code maps to 409s. They must match the Flyway migrations. */
object UserUniqueConstraints {
    const val NICKNAME = "uk_user_nickname"
    const val EXTERNAL_ID = "uk_user_external_id"
    const val SOCIAL_IDENTITY = "uk_social_account_provider_external"
}

/**
 * The violated constraint's name, or null when the cause carries none. MySQL reports it as
 * "table.index" (e.g. "user.uk_user_nickname"), so the table prefix is dropped.
 */
fun DataIntegrityViolationException.violatedConstraintName(): String? =
    (cause as? ConstraintViolationException)?.constraintName?.substringAfterLast('.')
