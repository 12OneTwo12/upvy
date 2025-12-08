package me.onetwo.upvy.domain.user.controller

import me.onetwo.upvy.domain.user.dto.FollowCheckResponse
import me.onetwo.upvy.domain.user.dto.FollowResponse
import me.onetwo.upvy.domain.user.dto.FollowStatsResponse
import me.onetwo.upvy.domain.user.dto.UserProfileResponse
import me.onetwo.upvy.domain.user.service.FollowService
import me.onetwo.upvy.infrastructure.security.util.toUserId
import me.onetwo.upvy.infrastructure.common.ApiPaths
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import java.security.Principal
import java.util.UUID

/**
 * 팔로우 관리 Controller
 *
 * 팔로우, 언팔로우, 팔로우 관계 확인 등의 API를 제공합니다.
 */
@RestController
@RequestMapping(ApiPaths.API_V1_FOLLOWS)
class FollowController(
    private val followService: FollowService
) {

    /**
     * 사용자 팔로우
     *
     * @param principal 인증된 사용자 Principal (Spring Security에서 자동 주입)
     * @param followingId 팔로우할 사용자 ID
     * @return 생성된 팔로우 관계
     */
    @PostMapping("/{followingId}")
    fun follow(
        principal: Mono<Principal>,
        @PathVariable followingId: UUID
    ): Mono<ResponseEntity<FollowResponse>> {
        return principal
            .toUserId()
            .flatMap { userId -> followService.follow(userId, followingId) }
            .map { follow -> ResponseEntity.status(HttpStatus.CREATED).body(FollowResponse.from(follow)) }
    }

    /**
     * 사용자 언팔로우
     *
     * @param principal 인증된 사용자 Principal (Spring Security에서 자동 주입)
     * @param followingId 언팔로우할 사용자 ID
     */
    @DeleteMapping("/{followingId}")
    fun unfollow(
        principal: Mono<Principal>,
        @PathVariable followingId: UUID
    ): Mono<ResponseEntity<Void>> {
        return principal
            .toUserId()
            .flatMap { userId -> followService.unfollow(userId, followingId) }
            .thenReturn(ResponseEntity.noContent().build())
    }

    /**
     * 팔로우 관계 확인
     *
     * @param principal 인증된 사용자 Principal (Spring Security에서 자동 주입)
     * @param followingId 팔로우 대상 사용자 ID
     * @return 팔로우 여부
     */
    @GetMapping("/check/{followingId}")
    fun checkFollowing(
        principal: Mono<Principal>,
        @PathVariable followingId: UUID
    ): Mono<ResponseEntity<FollowCheckResponse>> {
        return principal
            .toUserId()
            .flatMap { userId ->
                followService.isFollowing(userId, followingId)
                    .map { isFollowing -> ResponseEntity.ok(FollowCheckResponse(userId, followingId, isFollowing)) }
            }
    }

    /**
     * 사용자의 팔로우 통계 조회
     *
     * @param targetUserId 조회할 사용자 ID
     * @return 팔로워/팔로잉 수
     */
    @GetMapping("/stats/{targetUserId}")
    fun getFollowStats(
        @PathVariable targetUserId: UUID
    ): Mono<ResponseEntity<FollowStatsResponse>> {
        return Mono.zip(
            followService.getFollowerCount(targetUserId),
            followService.getFollowingCount(targetUserId)
        ).map { tuple ->
            ResponseEntity.ok(FollowStatsResponse(targetUserId, tuple.t1, tuple.t2))
        }
    }

    /**
     * 내 팔로우 통계 조회
     *
     * @param principal 인증된 사용자 Principal (Spring Security에서 자동 주입)
     * @return 팔로워/팔로잉 수
     */
    @GetMapping("/stats/me")
    fun getMyFollowStats(
        principal: Mono<Principal>
    ): Mono<ResponseEntity<FollowStatsResponse>> {
        return principal
            .toUserId()
            .flatMap { userId ->
                Mono.zip(
                    followService.getFollowerCount(userId),
                    followService.getFollowingCount(userId)
                ).map { tuple ->
                    ResponseEntity.ok(FollowStatsResponse(userId, tuple.t1, tuple.t2))
                }
            }
    }

    /**
     * 팔로워 목록 조회
     *
     * 해당 사용자를 팔로우하는 사용자들의 프로필 목록을 반환합니다.
     *
     * @param userId 조회할 사용자 ID
     * @return 팔로워 프로필 목록
     */
    @GetMapping("/followers/{userId}")
    fun getFollowers(
        @PathVariable userId: UUID
    ): Mono<ResponseEntity<List<UserProfileResponse>>> {
        return followService.getFollowers(userId)
            .collectList()
            .map { followers -> ResponseEntity.ok(followers) }
    }

    /**
     * 팔로잉 목록 조회
     *
     * 해당 사용자가 팔로우하는 사용자들의 프로필 목록을 반환합니다.
     *
     * @param userId 조회할 사용자 ID
     * @return 팔로잉 프로필 목록
     */
    @GetMapping("/following/{userId}")
    fun getFollowing(
        @PathVariable userId: UUID
    ): Mono<ResponseEntity<List<UserProfileResponse>>> {
        return followService.getFollowing(userId)
            .collectList()
            .map { following -> ResponseEntity.ok(following) }
    }
}
