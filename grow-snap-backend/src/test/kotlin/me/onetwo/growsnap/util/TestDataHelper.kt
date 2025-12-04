package me.onetwo.growsnap.util

import me.onetwo.growsnap.domain.analytics.repository.ContentInteractionRepository
import me.onetwo.growsnap.domain.content.model.Category
import me.onetwo.growsnap.domain.content.model.Content
import me.onetwo.growsnap.domain.content.model.ContentInteraction
import me.onetwo.growsnap.domain.content.model.ContentMetadata
import me.onetwo.growsnap.domain.content.model.ContentStatus
import me.onetwo.growsnap.domain.content.model.ContentType
import me.onetwo.growsnap.domain.content.repository.ContentRepository
import me.onetwo.growsnap.domain.user.model.OAuthProvider
import me.onetwo.growsnap.domain.user.model.User
import me.onetwo.growsnap.domain.user.model.UserProfile
import me.onetwo.growsnap.domain.user.model.UserRole
import me.onetwo.growsnap.domain.user.repository.UserProfileRepository
import me.onetwo.growsnap.domain.user.repository.UserRepository
import java.util.UUID

/**
 * 통합 테스트용 User와 UserProfile을 함께 생성합니다.
 */
fun createUserWithProfile(
    userRepository: UserRepository,
    userProfileRepository: UserProfileRepository,
    email: String = "test@example.com",
    provider: OAuthProvider = OAuthProvider.GOOGLE,
    providerId: String = UUID.randomUUID().toString(),
    role: UserRole = UserRole.USER,
    nickname: String = "user${UUID.randomUUID().toString().substring(0, 8)}"
): Pair<User, UserProfile> {
    val user = userRepository.save(
        User(
            email = email,
            provider = provider,
            providerId = providerId,
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
