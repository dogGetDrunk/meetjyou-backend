package com.dogGetDrunk.meetjyou.chat.room

import com.dogGetDrunk.meetjyou.party.Party
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.MapsId
import jakarta.persistence.OneToOne
import jakarta.persistence.PostLoad
import jakarta.persistence.PostPersist
import jakarta.persistence.Transient
import org.hibernate.annotations.JdbcTypeCode
import org.springframework.data.domain.Persistable
import java.sql.Types
import java.util.UUID

@Entity
class ChatRoom(

    @Id
    val id: Long = 0,

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "room_id")
    val party: Party,

    @Column(nullable = false, unique = true)
    @JdbcTypeCode(Types.VARCHAR)
    val uuid: UUID = UUID.randomUUID()

) : Persistable<Long> {

    @Transient
    private var _isNew = true

    override fun getId() = id
    override fun isNew() = _isNew

    @PostPersist
    @PostLoad
    fun markNotNew() {
        _isNew = false
    }
}
