package me.onetwo.upvy.infrastructure.exception

import org.springframework.http.HttpStatus

/**
 * 비즈니스 로직 예외의 기본 클래스
 *
 * 모든 도메인 예외는 이 클래스를 상속받아 구현합니다.
 * 각 예외는 고유한 에러 코드와 HTTP 상태 코드를 가집니다.
 *
 * ### 장점
 * - GlobalExceptionHandler에서 하나의 핸들러로 모든 비즈니스 예외 처리 가능
 * - 새로운 예외 추가 시 @ExceptionHandler 추가 불필요
 * - 일관된 에러 응답 형식 보장
 *
 * @property errorCode 애플리케이션 레벨 에러 코드 (예: "USER_NOT_FOUND")
 * @property httpStatus HTTP 상태 코드
 * @property message 사용자에게 표시할 에러 메시지
 */
abstract class BusinessException(
    val errorCode: String,
    val httpStatus: HttpStatus,
    override val message: String
) : RuntimeException(message)
