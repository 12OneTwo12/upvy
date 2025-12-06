package me.onetwo.growsnap.crawler.service

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import java.io.InputStream
import java.net.URL
import java.time.Duration

/**
 * 테스트용 Mock S3 서비스
 *
 * 테스트 환경에서 실제 S3 호출 없이 동작을 시뮬레이션합니다.
 */
@Service
@Profile("test")
@Primary
class MockS3Service : S3Service {

    companion object {
        private val logger = LoggerFactory.getLogger(MockS3Service::class.java)
    }

    private val uploadedFiles = mutableMapOf<String, String>()
    var uploadSuccess: Boolean = true

    override fun upload(localPath: String, s3Key: String, bucket: String?): String {
        logger.debug("MockS3Service.upload called: localPath={}, s3Key={}", localPath, s3Key)

        if (!uploadSuccess) {
            throw S3Exception("Mock upload failed: $s3Key")
        }

        uploadedFiles[s3Key] = localPath
        return s3Key
    }

    override fun upload(inputStream: InputStream, s3Key: String, contentType: String, bucket: String?): String {
        logger.debug("MockS3Service.upload (InputStream) called: s3Key={}", s3Key)

        if (!uploadSuccess) {
            throw S3Exception("Mock upload failed: $s3Key")
        }

        uploadedFiles[s3Key] = "stream:$contentType"
        return s3Key
    }

    override fun generatePresignedUrl(s3Key: String, bucket: String?, expiration: Duration): URL {
        logger.debug("MockS3Service.generatePresignedUrl called: s3Key={}", s3Key)
        return URL("https://mock-s3.example.com/$s3Key?presigned=true")
    }

    override fun delete(s3Key: String, bucket: String?) {
        logger.debug("MockS3Service.delete called: s3Key={}", s3Key)
        uploadedFiles.remove(s3Key)
    }

    override fun exists(s3Key: String, bucket: String?): Boolean {
        logger.debug("MockS3Service.exists called: s3Key={}", s3Key)
        return uploadedFiles.containsKey(s3Key)
    }

    /**
     * 테스트용: 업로드된 파일 목록 조회
     */
    fun getUploadedFiles(): Map<String, String> = uploadedFiles.toMap()

    /**
     * 테스트용: 업로드된 파일 목록 초기화
     */
    fun clear() {
        uploadedFiles.clear()
    }
}
