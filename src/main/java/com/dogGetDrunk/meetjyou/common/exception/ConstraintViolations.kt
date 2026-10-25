package com.dogGetDrunk.meetjyou.common.exception

import org.hibernate.exception.ConstraintViolationException
import org.springframework.dao.DataIntegrityViolationException

/**
 * The violated constraint's name, or null when the cause carries none. MySQL reports it as
 * "table.index" (e.g. "user.uk_user_nickname"), so the table prefix is dropped.
 */
fun DataIntegrityViolationException.violatedConstraintName(): String? =
    (cause as? ConstraintViolationException)?.constraintName?.substringAfterLast('.')
