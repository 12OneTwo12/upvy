package me.onetwo.upvy.infrastructure.config

import org.junit.jupiter.api.BeforeAll
import reactor.test.StepVerifier
import java.time.Duration

/**
 * Reactive 테스트 베이스 클래스
 *
 * 목적:
 * - MockK mocking 실수로 인한 무한 대기 방지
 * - 모든 Reactive 테스트에 기본 타임아웃 적용
 * - CI/CD 빌드 안정성 향상
 *
 * 사용법:
 * ```kotlin
 * class MyServiceTest : BaseReactiveTest() {
 *     @Test
 *     fun myTest() {
 *         StepVerifier.create(mono)
 *             .expectNext(value)
 *             .verifyComplete()  // ← 자동으로 10초 타임아웃 적용됨
 *     }
 * }
 * ```
 *
 * 타임아웃 설정:
 * - 기본값: 10초 (단위 테스트 충분, mocking 실수 빠른 감지)
 * - 개별 조정 가능: `.verify(Duration.ofSeconds(30))` 명시적 지정
 *
 * @see StepVerifier
 * @see <a href="https://github.com/12OneTwo12/upvy/issues/177">ISSUE-177</a>
 */
abstract class BaseReactiveTest {

    companion object {
        /**
         * 기본 타임아웃: 10초
         *
         * 선정 이유:
         * - 단위 테스트는 보통 1초 이내 완료
         * - 통합 테스트도 10초면 충분
         * - mocking 실수 시 빠른 피드백 (무한 대기 → 10초 후 실패)
         * - CI/CD에서도 합리적인 대기 시간
         */
        private val DEFAULT_TIMEOUT = Duration.ofSeconds(10)

        @BeforeAll
        @JvmStatic
        fun setupStepVerifier() {
            StepVerifier.setDefaultTimeout(DEFAULT_TIMEOUT)
            println("⏱️ [BaseReactiveTest] StepVerifier 기본 타임아웃: ${DEFAULT_TIMEOUT.seconds}초 설정 완료")
        }
    }
}
