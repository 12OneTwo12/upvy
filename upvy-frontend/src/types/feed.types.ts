/**
 * 피드 관련 타입 정의
 *
 * 백엔드 API 스펙과 100% 일치:
 * - upvy-backend/src/main/kotlin/me/onetwo/upvy/domain/feed/dto/
 * - FeedResponse.kt
 * - FeedItemResponse.kt
 * - CreatorInfoResponse.kt
 * - InteractionInfoResponse.kt
 * - SubtitleInfoResponse.kt
 */

/**
 * 콘텐츠 타입
 * 백엔드: me.onetwo.upvy.domain.content.model.ContentType
 */
export type ContentType = 'VIDEO' | 'PHOTO';

/**
 * 카테고리
 * 백엔드: me.onetwo.upvy.domain.content.model.Category
 */
export type Category =
  | 'LANGUAGE'
  | 'SCIENCE'
  | 'HISTORY'
  | 'MATHEMATICS'
  | 'ART'
  | 'STARTUP'
  | 'MARKETING'
  | 'PROGRAMMING'
  | 'DESIGN'
  | 'PRODUCTIVITY'
  | 'PSYCHOLOGY'
  | 'FINANCE'
  | 'HEALTH'
  | 'PARENTING'
  | 'COOKING'
  | 'TRAVEL'
  | 'HOBBY'
  | 'TREND'
  | 'OTHER'
  | 'FUN';

/**
 * 크리에이터 정보
 * 백엔드: CreatorInfoResponse
 */
export interface CreatorInfo {
  userId: string;
  nickname: string;
  profileImageUrl: string | null;
  followerCount?: number;
  isFollowing?: boolean; // 현재 사용자가 팔로우 중인지 여부
}

/**
 * 인터랙션 정보
 * 백엔드: InteractionInfoResponse
 */
export interface InteractionInfo {
  likeCount: number;
  commentCount: number;
  saveCount: number;
  shareCount: number;
  viewCount?: number;
  isLiked?: boolean; // 현재 사용자가 좋아요 했는지 여부
  isSaved?: boolean; // 현재 사용자가 저장했는지 여부
}

/**
 * 자막 정보
 * 백엔드: SubtitleInfoResponse
 */
export interface SubtitleInfo {
  language: string;
  subtitleUrl: string;
}

/**
 * 피드 아이템
 * 백엔드: FeedItemResponse
 */
export interface FeedItem {
  contentId: string;
  contentType: ContentType;
  url: string;
  photoUrls: string[] | null; // PHOTO 타입인 경우 사진 URL 목록
  thumbnailUrl: string;
  duration: number | null;
  width: number;
  height: number;
  title: string;
  description: string | null;
  category: Category;
  tags: string[];
  creator: CreatorInfo;
  interactions: InteractionInfo;
  subtitles: SubtitleInfo[];
}

/**
 * 커서 기반 페이지 응답
 * 백엔드: CursorPageResponse<T>
 */
export interface CursorPageResponse<T> {
  content: T[];
  nextCursor: string | null;
  hasNext: boolean;
  count: number;
}

/**
 * 피드 응답
 * 백엔드: FeedResponse = CursorPageResponse<FeedItemResponse>
 */
export type FeedResponse = CursorPageResponse<FeedItem>;

/**
 * 피드 요청 파라미터
 */
export interface FeedRequest {
  cursor?: string | null;
  limit?: number;
  language?: string;
}

/**
 * 피드 탭 타입
 */
export type FeedTab = 'recommended' | 'following';
