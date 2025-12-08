package me.onetwo.upvy.domain.user.service

import me.onetwo.upvy.domain.user.model.UserProfile
import org.springframework.http.codec.multipart.FilePart
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * 사용자 프로필 관리 서비스 인터페이스
 *
 * 프로필 생성, 수정, 조회 등의 비즈니스 로직을 정의합니다.
 */
interface UserProfileService {

    /**
     * 사용자 프로필 생성
     *
     * @param userId 사용자 ID
     * @param nickname 닉네임 (고유해야 함)
     * @param profileImageUrl 프로필 이미지 URL (선택 사항)
     * @param bio 자기소개 (선택 사항)
     * @return 생성된 프로필 정보
     * @throws me.onetwo.upvy.domain.user.exception.DuplicateNicknameException 닉네임이 중복된 경우
     */
    fun createProfile(
        userId: UUID,
        nickname: String,
        profileImageUrl: String? = null,
        bio: String? = null
    ): Mono<UserProfile>

    /**
     * 사용자 ID로 프로필 조회
     *
     * @param userId 사용자 ID
     * @return 프로필 정보
     * @throws me.onetwo.upvy.domain.user.exception.UserProfileNotFoundException 프로필을 찾을 수 없는 경우
     */
    fun getProfileByUserId(userId: UUID): Mono<UserProfile>

    /**
     * 닉네임으로 프로필 조회
     *
     * @param nickname 닉네임
     * @return 프로필 정보
     * @throws me.onetwo.upvy.domain.user.exception.UserProfileNotFoundException 프로필을 찾을 수 없는 경우
     */
    fun getProfileByNickname(nickname: String): Mono<UserProfile>

    /**
     * 닉네임 중복 확인
     *
     * @param nickname 확인할 닉네임
     * @return 중복 여부 (true: 중복, false: 사용 가능)
     */
    fun isNicknameDuplicated(nickname: String): Mono<Boolean>

    /**
     * 프로필 업데이트
     *
     * 닉네임, 프로필 이미지, 자기소개를 업데이트합니다.
     *
     * @param userId 사용자 ID
     * @param nickname 새 닉네임 (선택 사항)
     * @param profileImageUrl 새 프로필 이미지 URL (선택 사항)
     * @param bio 새 자기소개 (선택 사항)
     * @return 업데이트된 프로필 정보
     * @throws me.onetwo.upvy.domain.user.exception.UserProfileNotFoundException 프로필을 찾을 수 없는 경우
     * @throws me.onetwo.upvy.domain.user.exception.DuplicateNicknameException 닉네임이 중복된 경우
     */
    fun updateProfile(
        userId: UUID,
        nickname: String? = null,
        profileImageUrl: String? = null,
        bio: String? = null
    ): Mono<UserProfile>

    /**
     * 팔로워 수 증가
     *
     * @param userId 사용자 ID
     * @return 업데이트된 프로필 정보
     * @throws me.onetwo.upvy.domain.user.exception.UserProfileNotFoundException 프로필을 찾을 수 없는 경우
     */
    fun incrementFollowerCount(userId: UUID): Mono<UserProfile>

    /**
     * 팔로워 수 감소
     *
     * @param userId 사용자 ID
     * @return 업데이트된 프로필 정보
     * @throws me.onetwo.upvy.domain.user.exception.UserProfileNotFoundException 프로필을 찾을 수 없는 경우
     */
    fun decrementFollowerCount(userId: UUID): Mono<UserProfile>

    /**
     * 팔로잉 수 증가
     *
     * @param userId 사용자 ID
     * @return 업데이트된 프로필 정보
     * @throws me.onetwo.upvy.domain.user.exception.UserProfileNotFoundException 프로필을 찾을 수 없는 경우
     */
    fun incrementFollowingCount(userId: UUID): Mono<UserProfile>

    /**
     * 팔로잉 수 감소
     *
     * @param userId 사용자 ID
     * @return 업데이트된 프로필 정보
     * @throws me.onetwo.upvy.domain.user.exception.UserProfileNotFoundException 프로필을 찾을 수 없는 경우
     */
    fun decrementFollowingCount(userId: UUID): Mono<UserProfile>

    /**
     * 프로필 이미지 업로드
     *
     * FilePart를 받아서 이미지를 처리하고 S3에 업로드합니다.
     *
     * ### 처리 흐름
     * 1. FilePart에서 바이트 배열과 Content-Type 추출
     * 2. ImageUploadService를 통해 S3 업로드
     * 3. 업로드된 이미지 URL 반환
     *
     * @param userId 사용자 ID
     * @param filePart 업로드할 이미지 파일
     * @return 업로드된 이미지 URL을 담은 Mono
     * @throws IllegalArgumentException 이미지 유효성 검증 실패 시
     */
    fun uploadProfileImage(userId: UUID, filePart: FilePart): Mono<String>
}
