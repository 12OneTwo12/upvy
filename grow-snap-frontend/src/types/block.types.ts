/**
 * 차단 관련 타입 정의
 *
 * 백엔드 API 스펙:
 * - UserBlockController: grow-snap-backend/src/main/kotlin/me/onetwo/growsnap/domain/block/controller/UserBlockController.kt
 * - ContentBlockController: grow-snap-backend/src/main/kotlin/me/onetwo/growsnap/domain/block/controller/ContentBlockController.kt
 */

/**
 * 커서 기반 페이지네이션 응답
 *
 * 백엔드: CursorPageResponse<T>
 */
export interface CursorPageResponse<T> {
  content: T[];
  nextCursor: string | null;
  hasNext: boolean;
  count: number;
}

/**
 * 사용자 차단 응답
 *
 * 백엔드: UserBlockResponse
 * POST /api/v1/users/{userId}/block
 */
export interface UserBlockResponse {
  id: number;
  blockerId: string;
  blockedId: string;
  createdAt: string; // ISO 8601 format (Instant)
}

/**
 * 콘텐츠 차단 응답
 *
 * 백엔드: ContentBlockResponse
 * POST /api/v1/contents/{contentId}/block
 */
export interface ContentBlockResponse {
  id: number;
  userId: string;
  contentId: string;
  createdAt: string; // ISO 8601 format (Instant)
}

/**
 * 차단한 사용자 항목
 *
 * 백엔드: BlockedUserItemResponse
 */
export interface BlockedUser {
  blockId: number;
  userId: string;
  nickname: string;
  profileImageUrl: string | null;
  blockedAt: string; // ISO 8601 format (Instant)
}

/**
 * 차단한 콘텐츠 항목
 *
 * 백엔드: BlockedContentItemResponse
 */
export interface BlockedContent {
  blockId: number;
  contentId: string;
  title: string;
  thumbnailUrl: string;
  creatorNickname: string;
  blockedAt: string; // ISO 8601 format (Instant)
}

/**
 * 차단한 사용자 목록 응답
 *
 * 백엔드: BlockedUsersResponse = CursorPageResponse<BlockedUserItemResponse>
 * GET /api/v1/users/blocks?cursor=&limit=20
 */
export type BlockedUsersResponse = CursorPageResponse<BlockedUser>;

/**
 * 차단한 콘텐츠 목록 응답
 *
 * 백엔드: BlockedContentsResponse = CursorPageResponse<BlockedContentItemResponse>
 * GET /api/v1/contents/blocks?cursor=&limit=20
 */
export type BlockedContentsResponse = CursorPageResponse<BlockedContent>;

/**
 * 차단 타입
 */
export type BlockType = 'user' | 'content';
