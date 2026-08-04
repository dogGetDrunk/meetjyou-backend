package com.dogGetDrunk.meetjyou.post.dto

import com.fasterxml.jackson.annotation.JsonIgnore
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

data class CreatePostRequest(
    @field:Size(max = 20)
    @field:NotBlank
    val title: String,
    @field:Size(max = 500)
    @field:NotBlank
    val content: String,
    val isInstant: Boolean,
    val itinStart: Instant,
    val itinFinish: Instant,
    val location: String,
    @field:Min(1)
    @field:Max(10)
    val capacity: Int,
    @field:Valid
    val companionSpec: CompanionSpec?,
    val planUuid: UUID?,
    val isPlanPublic: Boolean?,
) {

    // @JsonIgnore is required, not cosmetic: Jackson's bean introspection treats any public
    // is-prefixed no-arg method as a serializable property regardless of @Schema(hidden=true)
    // (that annotation only affects Swagger docs). Without it, IdempotencyKeyService.hashRequest
    // would fold isItinStartAfterNow()'s Instant.now()-dependent result into the hash, so a
    // byte-identical retry sent moments later could hash differently and be misread as a conflict.
    @AssertTrue(message = "일정 시작 시각은 현재 시각 이후여야 합니다. (Buffer = 2 min)")
    @Schema(hidden = true)
    @JsonIgnore
    fun isItinStartAfterNow(): Boolean =
        !itinStart.truncatedTo(ChronoUnit.MINUTES)
            .isBefore(Instant.now().truncatedTo(ChronoUnit.MINUTES).minus(2, ChronoUnit.MINUTES))

    @AssertTrue(message = "일정 종료 시각은 일정 시작 시각 이후여야 합니다.")
    @Schema(hidden = true)
    @JsonIgnore
    fun isItinFinishAfterItinStart(): Boolean =
        itinFinish.isAfter(itinStart)
}
