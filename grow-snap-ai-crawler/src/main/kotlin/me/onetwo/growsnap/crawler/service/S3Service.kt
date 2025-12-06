package me.onetwo.growsnap.crawler.service

import io.awspring.cloud.s3.S3Template
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.File
import java.io.InputStream
import java.net.URL
import java.time.Duration

/**
 * S3 서비스 인터페이스
 *
 * AWS S3에 파일을 업로드하고 다운로드하는 기능을 제공합니다.
 */
interface S3Service {

    /**
     * 로컬 파일을 S3에 업로드
     *
     * @param localPath 로컬 파일 경로
     * @param s3Key S3 객체 키
     * @param bucket 버킷 이름 (기본: 원본 비디오 버킷)
     * @return 업로드된 S3 키
     */
    fun upload(localPath: String, s3Key: String, bucket: String? = null): String

    /**
     * InputStream을 S3에 업로드
     *
     * @param inputStream 업로드할 데이터 스트림
     * @param s3Key S3 객체 키
     * @param contentType 콘텐츠 타입
     * @param bucket 버킷 이름
     * @return 업로드된 S3 키
     */
    fun upload(inputStream: InputStream, s3Key: String, contentType: String, bucket: String? = null): String

    /**
     * S3 객체의 Presigned URL 생성
     *
     * @param s3Key S3 객체 키
     * @param bucket 버킷 이름
     * @param expiration URL 만료 시간
     * @return Presigned URL
     */
    fun generatePresignedUrl(s3Key: String, bucket: String? = null, expiration: Duration = Duration.ofHours(1)): URL

    /**
     * S3 객체 삭제
     *
     * @param s3Key S3 객체 키
     * @param bucket 버킷 이름
     */
    fun delete(s3Key: String, bucket: String? = null)

    /**
     * S3 객체 존재 여부 확인
     *
     * @param s3Key S3 객체 키
     * @param bucket 버킷 이름
     * @return 존재 여부
     */
    fun exists(s3Key: String, bucket: String? = null): Boolean
}

/**
 * S3 서비스 구현체
 */
@Service
class S3ServiceImpl(
    private val s3Template: S3Template,
    @Value("\${s3.bucket.raw-videos}") private val rawVideosBucket: String,
    @Value("\${s3.bucket.edited-videos}") private val editedVideosBucket: String,
    @Value("\${s3.bucket.thumbnails}") private val thumbnailsBucket: String
) : S3Service {

    companion object {
        private val logger = LoggerFactory.getLogger(S3ServiceImpl::class.java)
    }

    override fun upload(localPath: String, s3Key: String, bucket: String?): String {
        val targetBucket = bucket ?: rawVideosBucket
        logger.info("S3 업로드 시작: localPath={}, bucket={}, key={}", localPath, targetBucket, s3Key)

        try {
            val file = File(localPath)
            if (!file.exists()) {
                throw S3Exception("Local file not found: $localPath")
            }

            s3Template.upload(targetBucket, s3Key, file.inputStream())

            logger.info("S3 업로드 완료: bucket={}, key={}, size={}MB",
                targetBucket, s3Key, file.length() / (1024 * 1024))

            return s3Key

        } catch (e: S3Exception) {
            throw e
        } catch (e: Exception) {
            logger.error("S3 업로드 실패: localPath={}, bucket={}, key={}", localPath, targetBucket, s3Key, e)
            throw S3Exception("Failed to upload to S3: $s3Key", e)
        }
    }

    override fun upload(inputStream: InputStream, s3Key: String, contentType: String, bucket: String?): String {
        val targetBucket = bucket ?: rawVideosBucket
        logger.debug("S3 업로드 시작 (InputStream): bucket={}, key={}", targetBucket, s3Key)

        try {
            s3Template.upload(targetBucket, s3Key, inputStream)

            logger.debug("S3 업로드 완료 (InputStream): bucket={}, key={}", targetBucket, s3Key)
            return s3Key

        } catch (e: Exception) {
            logger.error("S3 업로드 실패 (InputStream): bucket={}, key={}", targetBucket, s3Key, e)
            throw S3Exception("Failed to upload to S3: $s3Key", e)
        }
    }

    override fun generatePresignedUrl(s3Key: String, bucket: String?, expiration: Duration): URL {
        val targetBucket = bucket ?: rawVideosBucket
        logger.debug("Presigned URL 생성: bucket={}, key={}", targetBucket, s3Key)

        try {
            val url = s3Template.createSignedGetURL(targetBucket, s3Key, expiration)
            logger.debug("Presigned URL 생성 완료: url={}", url)
            return url

        } catch (e: Exception) {
            logger.error("Presigned URL 생성 실패: bucket={}, key={}", targetBucket, s3Key, e)
            throw S3Exception("Failed to generate presigned URL: $s3Key", e)
        }
    }

    override fun delete(s3Key: String, bucket: String?) {
        val targetBucket = bucket ?: rawVideosBucket
        logger.debug("S3 객체 삭제: bucket={}, key={}", targetBucket, s3Key)

        try {
            s3Template.deleteObject(targetBucket, s3Key)
            logger.debug("S3 객체 삭제 완료: bucket={}, key={}", targetBucket, s3Key)

        } catch (e: Exception) {
            logger.error("S3 객체 삭제 실패: bucket={}, key={}", targetBucket, s3Key, e)
            throw S3Exception("Failed to delete from S3: $s3Key", e)
        }
    }

    override fun exists(s3Key: String, bucket: String?): Boolean {
        val targetBucket = bucket ?: rawVideosBucket

        return try {
            // S3Template에 objectExists가 없으므로 download 시도로 확인
            val resource = s3Template.download(targetBucket, s3Key)
            resource.exists()
        } catch (e: Exception) {
            logger.debug("S3 객체 존재하지 않음 또는 확인 실패: bucket={}, key={}", targetBucket, s3Key)
            false
        }
    }

    /**
     * 편집된 비디오 버킷에 업로드
     */
    fun uploadEditedVideo(localPath: String, s3Key: String): String {
        return upload(localPath, s3Key, editedVideosBucket)
    }

    /**
     * 썸네일 버킷에 업로드
     */
    fun uploadThumbnail(localPath: String, s3Key: String): String {
        return upload(localPath, s3Key, thumbnailsBucket)
    }
}

/**
 * S3 예외
 */
class S3Exception(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
