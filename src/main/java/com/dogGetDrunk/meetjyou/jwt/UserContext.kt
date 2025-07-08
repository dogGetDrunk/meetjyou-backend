package com.dogGetDrunk.meetjyou.jwt

import com.dogGetDrunk.meetjyou.user.User

object UserContext {
    private val userHolder: ThreadLocal<User> = ThreadLocal()

    fun setUser(user: User) {
        userHolder.set(user)
    }

    fun getUser(): User {
        return userHolder.get()
            ?: throw IllegalStateException("User not found in UserContext")
    }

    fun clear() {
        userHolder.remove()
    }
}
