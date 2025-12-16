package me.onetwo.upvy.infrastructure.config

import org.junit.jupiter.api.BeforeAll
import org.slf4j.LoggerFactory
import reactor.test.StepVerifier
import java.time.Duration

/**
 * Reactive 테스트 베이스 인터페이스
 *
 * 목적:
 * - MockK mocking 실수로 인한 무한 대기 방지
 * - 모든 Reactive 테스트에 기본 타임아웃 적용
 * - CI/CD 빌드 안정성 향상
 *
 * 설계:
 * - `interface`로 설계하여 다중 상속 가능 (예: `class MyTest : SomeBase(), BaseReactiveTest`)
 * - 다른 베이스 클래스를 상속해야 하는 테스트에도 유연하게 적용 가능
 *
 * 사용법:
 * ```kotlin
 * // 단독 사용
 * class MyServiceTest : BaseReactiveTest {
 *     @Test
 *     fun myTest() {
 *         StepVerifier.create(mono)
 *             .expectNext(value)
 *             .verifyComplete()  // ← 자동으로 10초 타임아웃 적용됨
 *     }
 * }
 *
 * // 다른 베이스 클래스와 함께 사용
 * class MyIntegrationTest : AbstractIntegrationTest(), BaseReactiveTest {
 *     // AbstractIntegrationTest의 기능 + BaseReactiveTest 타임아웃 보호
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
interface BaseReactiveTest {

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
        private val defaultTimeout = Duration.ofSeconds(10)

        private val log = LoggerFactory.getLogger(BaseReactiveTest::class.java)

        @BeforeAll
        @JvmStatic
        fun setupStepVerifier() {
            StepVerifier.setDefaultTimeout(defaultTimeout)
            log.debug("[BaseReactiveTest] StepVerifier 기본 타임아웃: ${defaultTimeout.seconds}초 설정 완료")
        }
    }
}
