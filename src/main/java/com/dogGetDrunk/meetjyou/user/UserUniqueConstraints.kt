package com.dogGetDrunk.meetjyou.user

/** Unique constraint names the code maps to 409s. They must match the Flyway migrations. */
object UserUniqueConstraints {
    const val NICKNAME = "uk_user_nickname"
    const val EXTERNAL_ID = "uk_user_external_id"
    const val SOCIAL_IDENTITY = "uk_social_account_provider_external"
}
