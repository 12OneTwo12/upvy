package me.onetwo.upvy.util

import me.onetwo.upvy.domain.analytics.repository.ContentInteractionRepository
import me.onetwo.upvy.domain.content.model.Category
import me.onetwo.upvy.domain.content.model.Content
import me.onetwo.upvy.domain.content.model.ContentInteraction
import me.onetwo.upvy.domain.content.model.ContentMetadata
import me.onetwo.upvy.domain.content.model.ContentStatus
import me.onetwo.upvy.domain.content.model.ContentType
import me.onetwo.upvy.domain.content.repository.ContentRepository
import me.onetwo.upvy.domain.user.model.User
import me.onetwo.upvy.domain.user.model.UserProfile
import me.onetwo.upvy.domain.user.model.UserRole
import me.onetwo.upvy.domain.user.repository.UserProfileRepository
import me.onetwo.upvy.domain.user.repository.UserRepository
import java.util.UUID

/**
 * 통합 테스트용 User와 UserProfile을 함께 생성합니다.
 *
 * 계정 통합 아키텍처 적용 후:
 * - provider, providerId 파라미터 제거
 * - User 모델에는 핵심 정보만 포함
 * - 인증 수단 정보는 user_authentication_methods 테이블에서 별도 관리
 */
fun createUserWithProfile(
    userRepository: UserRepository,
    userProfileRepository: UserProfileRepository,
    email: String = "test@example.com",
    role: UserRole = UserRole.USER,
    nickname: String = "user${UUID.randomUUID().toString().substring(0, 8)}"
): Pair<User, UserProfile> {
    val user = userRepository.save(
        User(
            email = email,
            role = role
        )
    ).block()!!

    val profile = userProfileRepository.save(
        UserProfile(
            userId = user.id!!,
            nickname = nickname
        )
    ).block()!!

    return Pair(user, profile)
}

/**
 * 통합 테스트용 Content, ContentMetadata, ContentInteraction을 함께 생성합니다.
 */
fun createContent(
    contentRepository: ContentRepository,
    creatorId: UUID,
    contentType: ContentType = ContentType.VIDEO,
    url: String = "https://example.com/video.mp4",
    thumbnailUrl: String = "https://example.com/thumb.jpg",
    duration: Int? = 60,
    width: Int = 1920,
    height: Int = 1080,
    status: ContentStatus = ContentStatus.PUBLISHED,
    title: String = "Test Content",
    description: String = "Test Description",
    category: Category = Category.LANGUAGE,
    language: String = "ko",
    contentInteractionRepository: ContentInteractionRepository? = null
): Content {
    // Content 저장
    val content = contentRepository.save(
        Content(
            id = UUID.randomUUID(),  // UUID 생성
            creatorId = creatorId,
            contentType = contentType,
            url = url,
            thumbnailUrl = thumbnailUrl,
            duration = duration,
            width = width,
            height = height,
            status = status
        )
    ).block()!!

    // ContentMetadata 저장 (getContent API에서 필수)
    contentRepository.saveMetadata(
        ContentMetadata(
            contentId = content.id!!,
            title = title,
            description = description,
            category = category,
            tags = emptyList(),
            language = language
        )
    ).block()

    // ContentInteraction 저장 (likeCount 등을 위해 필요)
    contentInteractionRepository?.create(
        ContentInteraction(
            contentId = content.id!!,
            likeCount = 0,
            commentCount = 0,
            saveCount = 0,
            shareCount = 0,
            viewCount = 0
        )
    )?.block()

    return content
}
