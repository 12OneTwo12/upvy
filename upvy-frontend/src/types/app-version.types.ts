/**
 * App Version 관련 타입 정의
 *
 * 백엔드: me.onetwo.upvy.domain.app
 */

/**
 * 플랫폼 타입
 */
export type Platform = 'IOS' | 'ANDROID';

/**
 * 앱 버전 체크 요청
 *
 * 백엔드: AppVersionCheckRequest
 */
export interface AppVersionCheckRequest {
  /** 플랫폼 (IOS, ANDROID) */
  platform: Platform;
  /** 현재 앱 버전 (시맨틱 버전 형식: major.minor.patch) */
  currentVersion: string;
}

/**
 * 앱 버전 체크 응답
 *
 * 백엔드: AppVersionCheckResponse
 */
export interface AppVersionCheckResponse {
  /** 강제 업데이트 필요 여부 */
  needsUpdate: boolean;
  /** 최신 버전 여부 */
  isLatestVersion: boolean;
  /** 최신 버전 */
  latestVersion: string;
  /** 최소 지원 버전 */
  minimumVersion: string;
  /** 앱스토어/플레이스토어 URL (업데이트 필요 시에만 제공) */
  storeUrl: string | null;
}
