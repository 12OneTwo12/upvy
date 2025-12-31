package me.onetwo.upvy.domain.quiz.controller

import jakarta.validation.Valid
import me.onetwo.upvy.domain.quiz.dto.*
import me.onetwo.upvy.domain.quiz.service.QuizService
import me.onetwo.upvy.infrastructure.common.ApiPaths
import me.onetwo.upvy.infrastructure.security.util.toUserId
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Mono
import java.security.Principal
import java.util.UUID

/**
 * 퀴즈 컨트롤러
 *
 * 퀴즈 생성, 조회, 수정, 삭제 및 퀴즈 시도 관련 HTTP 요청을 처리합니다.
 *
 * @property quizService 퀴즈 서비스
 */
@RestController
@RequestMapping(ApiPaths.API_V1)
class QuizController(
    private val quizService: QuizService
) {

    /**
     * 퀴즈 생성
     *
     * 콘텐츠에 퀴즈를 생성합니다.
     * 하나의 콘텐츠에는 하나의 퀴즈만 생성 가능합니다 (1:1 관계).
     *
     * POST /api/v1/contents/{contentId}/quiz
     *
     * @param principal 인증된 사용자 Principal
     * @param contentId 콘텐츠 ID
     * @param request 퀴즈 생성 요청
     * @return 생성된 퀴즈 응답 (HTTP 201 Created)
     */
    @PostMapping("/contents/{contentId}/quiz")
    fun createQuiz(
        principal: Mono<Principal>,
        @PathVariable contentId: UUID,
        @Valid @RequestBody request: QuizCreateRequest
    ): Mono<ResponseEntity<QuizResponse>> {
        return principal
            .toUserId()
            .flatMap { userId ->
                quizService.createQuiz(contentId, request, userId)
            }
            .map { response -> ResponseEntity.status(HttpStatus.CREATED).body(response) }
    }

    /**
     * 퀴즈 조회
     *
     * 콘텐츠의 퀴즈를 조회합니다.
     * 사용자가 이미 퀴즈를 풀었으면 정답이 공개되고, 풀지 않았으면 정답이 숨겨집니다.
     *
     * GET /api/v1/contents/{contentId}/quiz
     *
     * @param principal 인증된 사용자 Principal (Optional - 비로그인 사용자도 조회 가능)
     * @param contentId 콘텐츠 ID
     * @return 퀴즈 응답 (통계 포함)
     */
    @GetMapping("/contents/{contentId}/quiz")
    fun getQuiz(
        principal: Mono<Principal>,
        @PathVariable contentId: UUID
    ): Mono<ResponseEntity<QuizResponse>> {
        return principal
            .toUserId()
            .flatMap { userId ->
                quizService.getQuizByContentId(contentId, userId)
            }
            .switchIfEmpty(
                Mono.defer { quizService.getQuizByContentId(contentId, null) }
            )
            .map { response -> ResponseEntity.ok(response) }
    }

    /**
     * 퀴즈 수정
     *
     * 콘텐츠의 퀴즈를 수정합니다.
     * 기존 보기는 모두 삭제되고 새로운 보기로 대체됩니다.
     *
     * PUT /api/v1/contents/{contentId}/quiz
     *
     * @param principal 인증된 사용자 Principal
     * @param contentId 콘텐츠 ID
     * @param request 퀴즈 수정 요청
     * @return 수정된 퀴즈 응답
     */
    @PutMapping("/contents/{contentId}/quiz")
    fun updateQuiz(
        principal: Mono<Principal>,
        @PathVariable contentId: UUID,
        @Valid @RequestBody request: QuizUpdateRequest
    ): Mono<ResponseEntity<QuizResponse>> {
        return principal
            .toUserId()
            .flatMap { userId ->
                quizService.updateQuiz(contentId, request, userId)
            }
            .map { response -> ResponseEntity.ok(response) }
    }

    /**
     * 퀴즈 삭제
     *
     * 콘텐츠의 퀴즈를 삭제합니다 (Soft Delete).
     * 퀴즈와 함께 모든 보기도 삭제됩니다.
     *
     * DELETE /api/v1/contents/{contentId}/quiz
     *
     * @param principal 인증된 사용자 Principal
     * @param contentId 콘텐츠 ID
     * @return HTTP 204 No Content
     */
    @DeleteMapping("/contents/{contentId}/quiz")
    fun deleteQuiz(
        principal: Mono<Principal>,
        @PathVariable contentId: UUID
    ): Mono<ResponseEntity<Void>> {
        return principal
            .toUserId()
            .flatMap { userId ->
                quizService.deleteQuiz(contentId, userId)
            }
            .then(Mono.just(ResponseEntity.noContent().build<Void>()))
    }

    /**
     * 퀴즈 시도 제출
     *
     * 퀴즈에 대한 답변을 제출합니다.
     * 사용자는 동일한 퀴즈를 여러 번 시도할 수 있습니다 (교육 목적).
     * 제출 즉시 정답 여부와 통계가 반환됩니다.
     *
     * POST /api/v1/quizzes/{quizId}/attempts
     *
     * @param principal 인증된 사용자 Principal
     * @param quizId 퀴즈 ID
     * @param request 퀴즈 시도 요청 (선택한 보기 목록)
     * @return 퀴즈 시도 응답 (정답 여부 + 통계)
     */
    @PostMapping("/quizzes/{quizId}/attempts")
    fun submitQuizAttempt(
        principal: Mono<Principal>,
        @PathVariable quizId: UUID,
        @Valid @RequestBody request: QuizAttemptRequest
    ): Mono<ResponseEntity<QuizAttemptResponse>> {
        return principal
            .toUserId()
            .flatMap { userId ->
                quizService.submitQuizAttempt(quizId, userId, request)
            }
            .map { response -> ResponseEntity.status(HttpStatus.CREATED).body(response) }
    }

    /**
     * 사용자의 퀴즈 시도 기록 조회
     *
     * 현재 사용자의 특정 퀴즈에 대한 모든 시도 기록을 조회합니다.
     *
     * GET /api/v1/quizzes/{quizId}/my-attempts
     *
     * @param principal 인증된 사용자 Principal
     * @param quizId 퀴즈 ID
     * @return 사용자 퀴즈 시도 기록 응답
     */
    @GetMapping("/quizzes/{quizId}/my-attempts")
    fun getMyQuizAttempts(
        principal: Mono<Principal>,
        @PathVariable quizId: UUID
    ): Mono<ResponseEntity<UserQuizAttemptsResponse>> {
        return principal
            .toUserId()
            .flatMap { userId ->
                quizService.getUserQuizAttempts(quizId, userId)
            }
            .map { response -> ResponseEntity.ok(response) }
    }

    /**
     * 퀴즈 통계 조회
     *
     * 퀴즈의 전체 통계를 조회합니다.
     * 모든 사용자의 시도 기록과 각 보기별 선택 통계를 포함합니다.
     *
     * GET /api/v1/quizzes/{quizId}/stats
     *
     * @param quizId 퀴즈 ID
     * @return 퀴즈 통계 응답
     */
    @GetMapping("/quizzes/{quizId}/stats")
    fun getQuizStats(
        @PathVariable quizId: UUID
    ): Mono<ResponseEntity<QuizStatsResponse>> {
        return quizService.getQuizStats(quizId)
            .map { response -> ResponseEntity.ok(response) }
    }
}
