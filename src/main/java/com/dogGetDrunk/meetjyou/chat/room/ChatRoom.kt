package com.dogGetDrunk.meetjyou.chat.room

import com.dogGetDrunk.meetjyou.party.Party
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.MapsId
import jakarta.persistence.OneToOne
import org.hibernate.annotations.JdbcTypeCode
import java.sql.Types
import java.util.UUID

@Entity
class ChatRoom(

    @Id
    val id: Long, // Party의 id를 그대로 사용

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "room_id")
    val party: Party,

    @Column(nullable = false, unique = true)
    @JdbcTypeCode(Types.VARCHAR)
    val uuid: UUID = UUID.randomUUID()
)

