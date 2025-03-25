package com.dogGetDrunk.meetjyou.preference

import com.dogGetDrunk.meetjyou.user.User
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(name = "UserPreference")
class UserPreference(
    @ManyToOne
    var user: User,

    @ManyToOne
    var preference: Preference
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0
}
