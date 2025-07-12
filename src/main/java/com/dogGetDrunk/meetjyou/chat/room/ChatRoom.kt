package com.dogGetDrunk.meetjyou.chat.room

import com.dogGetDrunk.meetjyou.party.Party
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToOne
import java.util.UUID

@Entity
class ChatRoom(
    @Id
    @OneToOne
    @JoinColumn(name = "room_id") // party_id = PK + FK
    val party: Party,

    @Column(nullable = false, unique = true)
    val uuid: UUID = UUID.randomUUID()
)
