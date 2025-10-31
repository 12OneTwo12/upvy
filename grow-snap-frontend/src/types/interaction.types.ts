/**
 * 인터랙션 관련 타입 정의
 *
 * 백엔드 API 스펙과 100% 일치:
 * - grow-snap-backend/src/main/kotlin/me/onetwo/growsnap/domain/interaction/dto/
 */

/**
 * 좋아요 응답
 * 백엔드: LikeResponse
 */
export interface LikeResponse {
  contentId: string;
  likeCount: number;
  isLiked: boolean;
}

/**
 * 좋아요 개수 응답
 * 백엔드: LikeCountResponse
 */
export interface LikeCountResponse {
  contentId: string;
  likeCount: number;
}

/**
 * 좋아요 상태 응답
 * 백엔드: LikeStatusResponse
 */
export interface LikeStatusResponse {
  contentId: string;
  isLiked: boolean;
}

/**
 * 저장 응답
 * 백엔드: SaveResponse
 */
export interface SaveResponse {
  contentId: string;
  saveCount: number;
  isSaved: boolean;
}

/**
 * 저장 상태 응답
 * 백엔드: SaveStatusResponse
 */
export interface SaveStatusResponse {
  contentId: string;
  isSaved: boolean;
}

/**
 * 저장된 콘텐츠 응답
 * 백엔드: SavedContentResponse
 */
export interface SavedContentResponse {
  contentId: string;
  title: string;
  thumbnailUrl: string;
  duration: number | null;
  creatorNickname: string;
  savedAt: string;
}

/**
 * 공유 응답
 * 백엔드: ShareResponse
 */
export interface ShareResponse {
  contentId: string;
  shareCount: number;
}

/**
 * 공유 링크 응답
 * 백엔드: ShareLinkResponse
 */
export interface ShareLinkResponse {
  contentId: string;
  shareUrl: string;
}

/**
 * 댓글 작성 요청
 * 백엔드: CommentRequest
 */
export interface CommentRequest {
  content: string;
  parentCommentId?: string | null;
}

/**
 * 댓글 응답
 * 백엔드: CommentResponse
 */
export interface CommentResponse {
  id: string;
  contentId: string;
  userId: string;
  userNickname: string;
  userProfileImageUrl: string | null;
  content: string;
  parentCommentId: string | null;
  createdAt: string;
  replyCount: number;
  likeCount: number;
  isLiked: boolean;
}

/**
 * 댓글 목록 응답 (페이징)
 * 백엔드: CommentListResponse
 */
export interface CommentListResponse {
  comments: CommentResponse[];
  hasNext: boolean;
  nextCursor: string | null;
}

/**
 * 댓글 좋아요 응답
 * 백엔드: CommentLikeResponse
 */
export interface CommentLikeResponse {
  commentId: string;
  likeCount: number;
  isLiked: boolean;
}

/**
 * 댓글 좋아요 개수 응답
 * 백엔드: CommentLikeCountResponse
 */
export interface CommentLikeCountResponse {
  commentId: string;
  likeCount: number;
}

/**
 * 댓글 좋아요 상태 응답
 * 백엔드: CommentLikeStatusResponse
 */
export interface CommentLikeStatusResponse {
  commentId: string;
  isLiked: boolean;
}
