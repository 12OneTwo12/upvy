/**
 * Analytics 타입 정의
 *
 * 백엔드: me.onetwo.upvy.domain.analytics.dto
 */

/**
 * 시청 이벤트 요청
 *
 * 백엔드: ViewEventRequest
 */
export interface ViewEventRequest {
  /**
   * 시청한 콘텐츠 ID
   */
  contentId: string;

  /**
   * 시청 시간 (초 단위)
   * - 최소: 0
   */
  watchedDuration: number;

  /**
   * 시청 완료율 (0-100)
   * - 최소: 0
   * - 최대: 100
   */
  completionRate: number;

  /**
   * 스킵 여부
   * - true: 시청 기록만 저장, view_count 증가 안 함
   * - false: 시청 기록 저장 + view_count 증가
   */
  skipped: boolean;
}
