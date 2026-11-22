package com.dogGetDrunk.meetjyou.place.tour

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient

@Configuration
class TourApiConfig(
    private val props: TourApiProperties,
) {
    // The builder bean is prototype-scoped, so these timeouts stay local to this client.
    @Bean
    fun tourPlaceSource(restClientBuilder: RestClient.Builder, objectMapper: ObjectMapper): TourPlaceSource {
        val requestFactory = SimpleClientHttpRequestFactory()
        requestFactory.setConnectTimeout(props.connectTimeout)
        requestFactory.setReadTimeout(props.readTimeout)
        return TourApiClient(props, restClientBuilder.requestFactory(requestFactory).build(), objectMapper)
    }
}
