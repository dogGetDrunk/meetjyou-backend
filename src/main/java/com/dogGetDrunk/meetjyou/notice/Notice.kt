package com.dogGetDrunk.meetjyou.notice

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.annotations.UpdateTimestamp
import java.sql.Types
import java.time.LocalDateTime
import java.util.UUID

@Entity
class Notice(
    var title: String,
    var body: String
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @Column(nullable = false, unique = true)
    @JdbcTypeCode(Types.VARCHAR)
    val uuid: UUID = UUID.randomUUID()

    @CreationTimestamp
    val createdAt: LocalDateTime = LocalDateTime.now()

    @UpdateTimestamp
    val lastEditedAt: LocalDateTime = LocalDateTime.now()
}
