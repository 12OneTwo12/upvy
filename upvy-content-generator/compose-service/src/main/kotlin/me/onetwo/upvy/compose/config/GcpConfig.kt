package me.onetwo.upvy.compose.config

import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageOptions
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * GCP 설정
 */
@Configuration
class GcpConfig(
    @Value("\${gcp.project-id}")
    private val projectId: String
) {
    @Bean
    fun googleCredentials(): GoogleCredentials {
        return GoogleCredentials.getApplicationDefault()
    }

    @Bean
    fun storage(credentials: GoogleCredentials): Storage {
        return StorageOptions.newBuilder()
            .setProjectId(projectId)
            .setCredentials(credentials)
            .build()
            .service
    }
}
