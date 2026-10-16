package com.dogGetDrunk.meetjyou.user

enum class UserStatus {
    NORMAL,
    // Withdrawn but still inside the grace period (or waiting for the purge job): restorable by logging in.
    DELETED,
    // Grace period over: personal data stripped and social accounts released for re-registration.
    PURGED,
}
