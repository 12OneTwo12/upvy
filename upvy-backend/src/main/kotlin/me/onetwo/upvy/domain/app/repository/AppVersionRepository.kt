package me.onetwo.upvy.domain.app.repository

import me.onetwo.upvy.domain.app.model.AppVersion
import me.onetwo.upvy.domain.app.model.Platform
import reactor.core.publisher.Mono

/**
 * 앱 버전 레포지토리 인터페이스 (Reactive)
 *
 * 앱 버전 설정의 데이터베이스 CRUD를 담당합니다.
 * R2DBC를 통해 완전한 Non-blocking 처리를 지원합니다.
 */
interface AppVersionRepository {

    /**
     * 플랫폼별 앱 버전 설정을 조회합니다.
     *
     * 클라이언트가 버전 체크를 요청할 때 사용됩니다.
     *
     * @param platform 플랫폼 (IOS, ANDROID)
     * @return 앱 버전 설정 (없으면 empty Mono)
     */
    fun findByPlatform(platform: Platform): Mono<AppVersion>

    /**
     * 앱 버전 설정을 저장합니다.
     *
     * 관리자가 새로운 플랫폼의 버전 설정을 등록할 때 사용됩니다.
     *
     * @param appVersion 저장할 앱 버전 설정
     * @return 저장된 앱 버전 설정 (Auto Increment ID 포함, Mono)
     */
    fun save(appVersion: AppVersion): Mono<AppVersion>

    /**
     * 앱 버전 설정을 업데이트합니다.
     *
     * 관리자가 최소 버전 또는 최신 버전을 변경할 때 사용됩니다.
     *
     * @param appVersion 업데이트할 앱 버전 설정
     * @return 업데이트된 앱 버전 설정 (Mono)
     */
    fun update(appVersion: AppVersion): Mono<AppVersion>
}
