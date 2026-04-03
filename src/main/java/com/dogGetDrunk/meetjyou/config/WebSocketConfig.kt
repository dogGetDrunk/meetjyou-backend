package com.dogGetDrunk.meetjyou.config

import com.dogGetDrunk.meetjyou.chat.connection.ChatStompInterceptor
import com.dogGetDrunk.meetjyou.config.property.WebSocketProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.messaging.simp.config.ChannelRegistration
import org.springframework.messaging.simp.config.MessageBrokerRegistry
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker
import org.springframework.web.socket.config.annotation.StompEndpointRegistry
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer

@Configuration
@EnableWebSocketMessageBroker
@EnableConfigurationProperties(WebSocketProperties::class)
class WebSocketConfig(
    private val chatStompInterceptor: ChatStompInterceptor,
    private val webSocketProperties: WebSocketProperties,
) : WebSocketMessageBrokerConfigurer {

    override fun configureMessageBroker(registry: MessageBrokerRegistry) {
        registry.enableSimpleBroker("/sub")
        registry.setApplicationDestinationPrefixes("/pub")
    }

    override fun registerStompEndpoints(registry: StompEndpointRegistry) {
        registry.addEndpoint("/ws-chat")
            .setAllowedOriginPatterns(*webSocketProperties.allowedOriginPatterns.toTypedArray())
    }

    override fun configureClientInboundChannel(registration: ChannelRegistration) {
        registration.interceptors(chatStompInterceptor)
    }
}
