package me.onetwo.growsnap.infrastructure.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.presigner.S3Presigner

/**
 * AWS S3 설정
 *
 * S3Client와 S3Presigner Bean을 생성합니다.
 *
 * ## 자격 증명 관리
 * `DefaultCredentialsProvider`를 사용하여 AWS 자격 증명을 안전하게 관리합니다.
 * 자격 증명은 다음 순서로 자동 탐색됩니다:
 * 1. 환경 변수 (AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY)
 * 2. 시스템 프로퍼티
 * 3. 자격 증명 파일 (~/.aws/credentials)
 * 4. IAM 역할 (EC2/ECS/Lambda 등에서 실행 시)
 *
 * 주의: 테스트 환경에서는 application-test.yml의 자격 증명을 환경 변수로 설정해야 합니다.
 */
@Configuration
class S3Config {

    @Value("\${spring.cloud.aws.region.static}")
    private lateinit var region: String

    /**
     * S3Client Bean
     *
     * S3에 파일을 업로드하거나 삭제할 때 사용합니다.
     * DefaultCredentialsProvider를 사용하여 자격 증명을 자동으로 탐색합니다.
     */
    @Bean
    fun s3Client(): S3Client {
        return S3Client.builder()
            .region(Region.of(region))
            .credentialsProvider(DefaultCredentialsProvider.create())
            .build()
    }

    /**
     * S3Presigner Bean
     *
     * S3 Presigned URL을 생성할 때 사용합니다.
     * DefaultCredentialsProvider를 사용하여 자격 증명을 자동으로 탐색합니다.
     */
    @Bean
    fun s3Presigner(): S3Presigner {
        return S3Presigner.builder()
            .region(Region.of(region))
            .credentialsProvider(DefaultCredentialsProvider.create())
            .build()
    }
}
