/**
 * 커서 기반 페이지네이션 타입 정의
 *
 * 백엔드 CursorPageResponse와 일치:
 * - upvy-backend/.../infrastructure/common/dto/CursorPageResponse.kt
 */

/**
 * 커서 페이지 응답
 * 백엔드: CursorPageResponse<T>
 */
export interface CursorPageResponse<T> {
  content: T[];
  nextCursor: string | null;
  hasNext: boolean;
  count: number;
}

/**
 * 커서 페이지 요청 파라미터
 */
export interface CursorPageParams {
  cursor?: string;
  limit?: number;
}
