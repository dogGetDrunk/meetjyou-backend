package com.dogGetDrunk.meetjyou.image

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import lombok.NoArgsConstructor
import java.util.*

@Entity
@Table(name = "user_profile_images")
@NoArgsConstructor
class UserProfileImage(

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    val id: UUID = UUID.randomUUID(), // 랜덤 UUID 생성

    @Column(nullable = false, unique = true)
    val userId: Long
)
