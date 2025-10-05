package com.dogGetDrunk.meetjyou.config

import com.oracle.bmc.auth.AuthenticationDetailsProvider
import com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider
import com.oracle.bmc.auth.InstancePrincipalsAuthenticationDetailsProvider
import com.oracle.bmc.objectstorage.ObjectStorageClient
import com.oracle.bmc.workrequests.WorkRequestClient
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(OracleProps::class)
class OracleStorageConfig {

    @Bean
    fun ociAuthProvider(props: OracleProps): AuthenticationDetailsProvider =
        when (props.auth.mode) {
            OracleProps.Mode.INSTANCE ->
                InstancePrincipalsAuthenticationDetailsProvider.builder().build()

            OracleProps.Mode.FILE -> {
                require(!props.auth.configFilePath.isNullOrBlank()) {
                    "oracle.oci.auth.config-file must be set for FILE mode"
                }
                ConfigFileAuthenticationDetailsProvider(props.auth.configFilePath, props.auth.profile)
            }
        } as AuthenticationDetailsProvider

    @Bean
    fun objectStorageClient(authProvider: AuthenticationDetailsProvider): ObjectStorageClient {
        return ObjectStorageClient.builder().build(authProvider)
    }

    @Bean
    fun workRequestClient(authProvider: AuthenticationDetailsProvider): WorkRequestClient {
        return WorkRequestClient.builder().build(authProvider)
    }
}
