package me.onetwo.growsnap.domain.content.repository

import me.onetwo.growsnap.domain.content.model.UploadSession
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

/**
 * 업로드 세션 레포지토리
 *
 * Redis를 사용하여 업로드 세션 정보를 임시 저장합니다.
 * TTL(15분) 이후 자동으로 삭제됩니다.
 */
@Repository
interface UploadSessionRepository : CrudRepository<UploadSession, String>
