package me.onetwo.upvy.compose.service

import com.google.cloud.storage.BlobId
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.Storage
import me.onetwo.upvy.compose.config.GcsException
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

private val logger = KotlinLogging.logger {}

/**
 * Google Cloud Storage 서비스
 *
 * GCS에서 파일 다운로드/업로드를 담당합니다.
 */
@Service
class GcsService(
    private val storage: Storage,
    @Value("\${ffmpeg.temp-dir}")
    private val tempDir: String
) {

    /**
     * GCS에서 파일 다운로드
     *
     * @param gcsUri GCS URI (gs://bucket/path/to/file)
     * @return 다운로드된 로컬 파일
     */
    fun download(gcsUri: String): File {
        try {
            val (bucket, objectName) = parseGcsUri(gcsUri)
            logger.debug { "GCS 다운로드: bucket=$bucket, object=$objectName" }

            val blob = storage.get(BlobId.of(bucket, objectName))
                ?: throw GcsException("GCS 객체를 찾을 수 없습니다: $gcsUri")

            // 임시 디렉토리에 파일 저장
            val fileName = objectName.substringAfterLast("/")
            val localPath = Path.of(tempDir, fileName)
            Files.createDirectories(localPath.parent)

            blob.downloadTo(localPath)
            logger.debug { "다운로드 완료: $localPath (${blob.size} bytes)" }

            return localPath.toFile()
        } catch (e: GcsException) {
            throw e
        } catch (e: Exception) {
            throw GcsException("GCS 다운로드 실패: $gcsUri", e)
        }
    }

    /**
     * 로컬 파일을 GCS에 업로드
     *
     * @param localFile 업로드할 로컬 파일
     * @param gcsUri 대상 GCS URI
     * @return 업로드된 GCS URI
     */
    fun upload(localFile: File, gcsUri: String): String {
        try {
            val (bucket, objectName) = parseGcsUri(gcsUri)
            logger.debug { "GCS 업로드: $localFile -> bucket=$bucket, object=$objectName" }

            val blobId = BlobId.of(bucket, objectName)
            val blobInfo = BlobInfo.newBuilder(blobId)
                .setContentType(detectContentType(localFile.name))
                .build()

            storage.createFrom(blobInfo, localFile.toPath())
            logger.debug { "업로드 완료: $gcsUri (${localFile.length()} bytes)" }

            return gcsUri
        } catch (e: Exception) {
            throw GcsException("GCS 업로드 실패: $gcsUri", e)
        }
    }

    /**
     * GCS Public URL 생성
     *
     * @param gcsUri GCS URI
     * @return Public URL
     */
    fun getPublicUrl(gcsUri: String): String {
        val (bucket, objectName) = parseGcsUri(gcsUri)
        return "https://storage.googleapis.com/$bucket/$objectName"
    }

    /**
     * GCS URI 파싱
     *
     * @param gcsUri gs://bucket/path/to/file 형식
     * @return Pair(bucket, objectName)
     */
    private fun parseGcsUri(gcsUri: String): Pair<String, String> {
        require(gcsUri.startsWith("gs://")) { "올바른 GCS URI 형식이 아닙니다: $gcsUri" }

        val withoutPrefix = gcsUri.removePrefix("gs://")
        val bucket = withoutPrefix.substringBefore("/")
        val objectName = withoutPrefix.substringAfter("/")

        return bucket to objectName
    }

    /**
     * 파일 확장자로 Content-Type 추론
     */
    private fun detectContentType(fileName: String): String {
        return when (fileName.substringAfterLast(".").lowercase()) {
            "mp4" -> "video/mp4"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "srt" -> "text/plain"
            else -> "application/octet-stream"
        }
    }
}
