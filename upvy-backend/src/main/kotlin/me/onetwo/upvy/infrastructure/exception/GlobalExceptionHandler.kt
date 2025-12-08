package me.onetwo.upvy.infrastructure.exception

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.bind.support.WebExchangeBindException
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

/**
 * 전역 예외 처리 핸들러
 *
 * 애플리케이션 전체에서 발생하는 예외를 중앙에서 처리하고
 * 통일된 형식의 에러 응답을 제공합니다.
 *
 * ### 설계 원칙
 * - **단일 핸들러 패턴**: BusinessException 하나로 모든 도메인 예외 처리
 * - 새로운 예외 추가 시 @ExceptionHandler 추가 불필요
 * - 예외 클래스에 errorCode와 httpStatus 포함
 * - @RestControllerAdvice로 모든 컨트롤러의 예외 처리
 * - Mono<ResponseEntity<ErrorResponse>>로 reactive 지원
 * - 로깅을 통한 에러 추적
 * - 민감 정보 노출 방지
 *
 * ### 장점
 * - 확장성: 새로운 도메인 예외 추가 시 코드 변경 불필요
 * - 일관성: 모든 비즈니스 예외가 동일한 형식으로 처리됨
 * - 유지보수성: 중복 코드 제거, 단일 책임 원칙 준수
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    /**
     * 비즈니스 예외 통합 처리
     *
     * BusinessException을 상속받는 모든 도메인 예외를 처리합니다.
     * 각 예외는 errorCode와 httpStatus를 가지고 있어, 하나의 핸들러로 처리 가능합니다.
     *
     * ### 처리 가능한 예외
     * - UserNotFoundException (404)
     * - UserProfileNotFoundException (404)
     * - DuplicateNicknameException (409)
     * - DuplicateEmailException (409)
     * - AlreadyFollowingException (409)
     * - CannotFollowSelfException (400)
     * - NotFollowingException (400)
     * - 기타 BusinessException을 상속받는 모든 예외
     *
     * @param ex BusinessException
     * @param exchange ServerWebExchange
     * @return 예외에 정의된 HTTP 상태 코드와 에러 응답
     */
    @ExceptionHandler(BusinessException::class)
    fun handleBusinessException(
        ex: BusinessException,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<ErrorResponse>> {
        logger.warn("Business exception occurred: errorCode={}, message={}", ex.errorCode, ex.message)

        val errorResponse = ErrorResponse(
            status = ex.httpStatus.value(),
            error = ex.httpStatus.reasonPhrase,
            message = ex.message,
            path = exchange.request.path.value(),
            code = ex.errorCode
        )

        return Mono.just(ResponseEntity.status(ex.httpStatus).body(errorResponse))
    }

    /**
     * Bean Validation 예외 처리
     *
     * @param ex WebExchangeBindException
     * @param exchange ServerWebExchange
     * @return 400 Bad Request 응답
     */
    @ExceptionHandler(WebExchangeBindException::class)
    fun handleValidationException(
        ex: WebExchangeBindException,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<ErrorResponse>> {
        logger.warn("Validation error: {}", ex.bindingResult.allErrors)

        val message = ex.bindingResult.fieldErrors
            .joinToString(", ") { "${it.field}: ${it.defaultMessage}" }

        val errorResponse = ErrorResponse(
            status = HttpStatus.BAD_REQUEST.value(),
            error = "Bad Request",
            message = message.ifEmpty { "입력값이 유효하지 않습니다." },
            path = exchange.request.path.value(),
            code = "VALIDATION_ERROR"
        )

        return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse))
    }

    /**
     * IllegalStateException 예외 처리
     *
     * @param ex IllegalStateException
     * @param exchange ServerWebExchange
     * @return 400 Bad Request 응답
     */
    @ExceptionHandler(IllegalStateException::class)
    fun handleIllegalStateException(
        ex: IllegalStateException,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<ErrorResponse>> {
        logger.warn("Illegal state: {}", ex.message)

        val errorResponse = ErrorResponse(
            status = HttpStatus.BAD_REQUEST.value(),
            error = "Bad Request",
            message = ex.message ?: "잘못된 요청입니다.",
            path = exchange.request.path.value(),
            code = "ILLEGAL_STATE"
        )

        return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse))
    }

    /**
     * IllegalArgumentException 예외 처리
     *
     * 토큰 관련 에러(오래된 토큰 등)는 401 Unauthorized로 응답합니다.
     *
     * @param ex IllegalArgumentException
     * @param exchange ServerWebExchange
     * @return 400 Bad Request 또는 401 Unauthorized 응답
     */
    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgumentException(
        ex: IllegalArgumentException,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<ErrorResponse>> {
        // 토큰 관련 에러인지 확인
        val isTokenError = ex.message?.contains("token", ignoreCase = true) ?: false

        if (isTokenError) {
            logger.warn("Invalid authentication token: {}", ex.message)
        } else {
            logger.warn("Illegal argument: {}", ex.message, ex)
        }

        // 토큰 에러인 경우 401 Unauthorized로 응답
        val httpStatus = if (isTokenError) HttpStatus.UNAUTHORIZED else HttpStatus.BAD_REQUEST
        val errorCode = if (isTokenError) "INVALID_TOKEN" else "ILLEGAL_ARGUMENT"

        val errorResponse = ErrorResponse(
            status = httpStatus.value(),
            error = httpStatus.reasonPhrase,
            message = ex.message ?: "잘못된 인자입니다.",
            path = exchange.request.path.value(),
            code = errorCode
        )

        return Mono.just(ResponseEntity.status(httpStatus).body(errorResponse))
    }

    /**
     * 기타 모든 예외 처리
     *
     * @param ex Exception
     * @param exchange ServerWebExchange
     * @return 500 Internal Server Error 응답
     */
    @ExceptionHandler(Exception::class)
    fun handleGeneralException(
        ex: Exception,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<ErrorResponse>> {
        logger.error("Unexpected error occurred", ex)

        val errorResponse = ErrorResponse(
            status = HttpStatus.INTERNAL_SERVER_ERROR.value(),
            error = "Internal Server Error",
            message = "서버 오류가 발생했습니다. 잠시 후 다시 시도해주세요.",
            path = exchange.request.path.value(),
            code = "INTERNAL_ERROR"
        )

        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse))
    }
}
