package com.dogGetDrunk.meetjyou.chat.message

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.util.UUID

data class ChatMessageRequest(
    val roomUuid: UUID,

    @field:NotBlank(message = "Message must not be blank.")
    @field:Size(max = 1000, message = "Message must not exceed 1000 characters.")
    val message: String,
)
