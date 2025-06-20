package com.dogGetDrunk.meetjyou.config

import com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider
import com.oracle.bmc.objectstorage.ObjectStorageClient
import com.oracle.bmc.workrequests.WorkRequestClient
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.nio.file.Paths

@Configuration
class OracleStorageConfig {

    @Bean
    fun ociAuthProvider(): ConfigFileAuthenticationDetailsProvider {
        val configPath = Paths.get(System.getProperty("user.home"), ".oci", "config").toString()
        return ConfigFileAuthenticationDetailsProvider(configPath, "DEFAULT")
    }

    @Bean
    fun objectStorageClient(authProvider: ConfigFileAuthenticationDetailsProvider): ObjectStorageClient {
        return ObjectStorageClient.builder().build(authProvider)
    }

    @Bean
    fun workRequestClient(authProvider: ConfigFileAuthenticationDetailsProvider): WorkRequestClient {
        return WorkRequestClient.builder().build(authProvider)
    }
}
