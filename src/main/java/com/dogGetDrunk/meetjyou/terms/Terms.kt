package com.dogGetDrunk.meetjyou.terms

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import java.sql.Types
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "terms")
class Terms(
    @Enumerated(EnumType.STRING)
    val type: TermsType,
    val version: String,
    @Column(name = "display_text")
    val displayText: String,
    val required: Boolean,
    @Column(name = "content_object_key")
    val contentObjectKey: String,
    @Column(name = "content_hash")
    val contentHash: String,
    @Enumerated(EnumType.STRING)
    val status: TermsStatus = TermsStatus.ACTIVE,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    @JdbcTypeCode(Types.VARCHAR)
    val uuid: UUID = UUID.randomUUID()

    @Column(name = "effective_at")
    var effectiveAt: Instant? = null
        protected set

    @Column(name = "created_at", insertable = false, updatable = false)
    var createdAt: Instant? = null
        protected set

    @Column(name = "updated_at", insertable = false, updatable = false)
    var updatedAt: Instant? = null
        protected set
}
