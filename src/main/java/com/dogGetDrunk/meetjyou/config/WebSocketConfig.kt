package com.dogGetDrunk.meetjyou.config

import com.dogGetDrunk.meetjyou.chat.connection.ChatStompErrorHandler
import com.dogGetDrunk.meetjyou.chat.connection.ChatStompInterceptor
import com.dogGetDrunk.meetjyou.chat.connection.ChatWebSocketSessionRegistry
import com.dogGetDrunk.meetjyou.config.property.WebSocketProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.messaging.simp.config.ChannelRegistration
import org.springframework.messaging.simp.config.MessageBrokerRegistry
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker
import org.springframework.web.socket.config.annotation.StompEndpointRegistry
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration

@Configuration
@EnableWebSocketMessageBroker
@EnableConfigurationProperties(WebSocketProperties::class)
class WebSocketConfig(
    private val chatStompInterceptor: ChatStompInterceptor,
    private val chatWebSocketSessionRegistry: ChatWebSocketSessionRegistry,
    private val chatStompErrorHandler: ChatStompErrorHandler,
    private val webSocketProperties: WebSocketProperties,
) : WebSocketMessageBrokerConfigurer {

    override fun configureMessageBroker(registry: MessageBrokerRegistry) {
        // /queue carries per-session replies (@SendToUser), e.g. /user/queue/errors for a failed SEND.
        registry.enableSimpleBroker("/sub", "/queue")
        registry.setApplicationDestinationPrefixes("/pub")
    }

    override fun registerStompEndpoints(registry: StompEndpointRegistry) {
        registry.addEndpoint("/ws-chat")
            .setAllowedOriginPatterns(*webSocketProperties.allowedOriginPatterns.toTypedArray())
        registry.setErrorHandler(chatStompErrorHandler)
    }

    override fun configureClientInboundChannel(registration: ChannelRegistration) {
        registration.interceptors(chatStompInterceptor)
    }

    // Keeps a handle on every open session so a kick / leave / withdrawal can close it.
    override fun configureWebSocketTransport(registration: WebSocketTransportRegistration) {
        registration.addDecoratorFactory(chatWebSocketSessionRegistry)
    }
}
