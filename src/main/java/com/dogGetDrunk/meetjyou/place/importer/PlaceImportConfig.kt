package com.dogGetDrunk.meetjyou.place.importer

import com.dogGetDrunk.meetjyou.place.RegionCatalog
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.client.RestClient

@Configuration
class PlaceImportConfig(
    private val props: PlaceImportProperties,
) {
    // The builder bean is prototype-scoped, so the timeouts and base URL stay local to this client.
    @Bean
    fun sbizFileSource(restClientBuilder: RestClient.Builder, objectMapper: ObjectMapper): SbizFileSource {
        val requestFactory = SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(props.connectTimeout)
            setReadTimeout(props.readTimeout)
        }
        val restClient = restClientBuilder.baseUrl(props.baseUrl).requestFactory(requestFactory).build()
        return SbizFileClient(props, restClient, objectMapper)
    }

    @Bean
    fun sbizZipReader(regionCatalog: RegionCatalog): SbizZipReader = SbizZipReader(regionCatalog)

    @Bean
    fun placeImportWriter(
        jdbcTemplate: JdbcTemplate,
        transactionManager: PlatformTransactionManager,
    ): PlaceImportWriter = PlaceImportWriter(jdbcTemplate, TransactionTemplate(transactionManager), props.chunkSize)
}
