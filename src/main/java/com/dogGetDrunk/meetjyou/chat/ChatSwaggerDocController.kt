package com.dogGetDrunk.meetjyou.chat

import com.dogGetDrunk.meetjyou.chat.message.ChatMessageRequest
import com.dogGetDrunk.meetjyou.chat.message.ChatMessageResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.parameters.RequestBody
import io.swagger.v3.oas.annotations.responses.ApiResponse
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/docs/chat")
class ChatSwaggerDocController {

    @Operation(
        summary = "💬 채팅 메시지 전송 (WebSocket)",
        description = """
            이 API는 실제로 사용되지 않으며, Swagger 문서용 설명입니다.
            
            WebSocket 연결 후 `/pub/chat/message`로 다음과 같은 JSON을 전송하세요.
            응답은 `/sub/chat/room/{room_uuid}`를 구독해서 받습니다.
        """,
        requestBody = RequestBody(
            required = true,
            content = [
                Content(schema = Schema(implementation = ChatMessageRequest::class))
            ]
        ),
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "성공적으로 전송됨",
                content = [Content(schema = Schema(implementation = ChatMessageResponse::class))]
            )
        ]
    )
    @PostMapping("/message")
    fun sendMessageDoc(@RequestBody request: ChatMessageRequest): ChatMessageResponse {
        return ChatMessageResponse(
            uuid = "example-uuid",
            senderNickname = "nickname",
            senderUuid = request.roomUuid.toString(),
            roomUuid = request.roomUuid.toString(),
            body = request.message,
            createdAt = java.time.LocalDateTime.now()
        )
    }
}
