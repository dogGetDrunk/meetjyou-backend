package com.dogGetDrunk.meetjyou.config

import org.springframework.context.annotation.Configuration
import org.springframework.messaging.simp.config.MessageBrokerRegistry
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker
import org.springframework.web.socket.config.annotation.StompEndpointRegistry
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer

@Configuration
@EnableWebSocketMessageBroker
class WebSocketConfig : WebSocketMessageBrokerConfigurer {

    override fun registerStompEndpoints(registry: StompEndpointRegistry) {
        registry.addEndpoint("/ws-chat") // 클라이언트가 연결할 WebSocket 엔드포인트
            .setAllowedOriginPatterns("*") // CORS 허용
            // .withSockJS() // 브라우저 환경이라면 사용. Android/iOS는 필요 없음
    }

    override fun configureMessageBroker(registry: MessageBrokerRegistry) {
        // 클라이언트가 메시지를 보낼 때 사용하는 prefix
        registry.setApplicationDestinationPrefixes("/pub")

        // 클라이언트가 구독할 수 있는 prefix
        registry.enableSimpleBroker("/sub")
    }
}
