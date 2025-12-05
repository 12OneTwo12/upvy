/**
 * 콘텐츠 관련 타입 정의
 *
 * 백엔드 API 스펙에 정확히 맞춰 작성되었습니다.
 * 참조: grow-snap-backend/src/main/kotlin/me/onetwo/growsnap/domain/content/
 */

/**
 * 콘텐츠 타입
 * 백엔드: ContentType.kt
 */
export type ContentType = 'VIDEO' | 'PHOTO';

/**
 * 콘텐츠 상태
 * 백엔드: ContentStatus.kt
 */
export type ContentStatus = 'PENDING' | 'PUBLISHED' | 'DELETED';

/**
 * 난이도 레벨
 * 백엔드: DifficultyLevel.kt
 */
export type DifficultyLevel = 'BEGINNER' | 'INTERMEDIATE' | 'ADVANCED';

/**
 * 콘텐츠 카테고리
 * 백엔드: Category.kt
 */
export type Category =
  // 학문 & 교육
  | 'LANGUAGE'
  | 'SCIENCE'
  | 'HISTORY'
  | 'MATHEMATICS'
  | 'ART'
  // 비즈니스 & 커리어
  | 'STARTUP'
  | 'MARKETING'
  | 'PROGRAMMING'
  | 'DESIGN'
  // 자기계발
  | 'PRODUCTIVITY'
  | 'PSYCHOLOGY'
  | 'FINANCE'
  | 'HEALTH'
  // 라이프스타일
  | 'PARENTING'
  | 'COOKING'
  | 'TRAVEL'
  | 'HOBBY'
  // 트렌드 & 인사이트
  | 'TREND'
  | 'OTHER'
  // 재미 & 가벼운 콘텐츠
  | 'FUN';

/**
 * 카테고리 표시 정보
 */
export interface CategoryInfo {
  value: Category;
  displayName: string;
  description: string;
}

/**
 * 모든 카테고리 목록 (백엔드 Category.kt와 동일)
 */
export const CATEGORIES: CategoryInfo[] = [
  // 학문 & 교육
  { value: 'LANGUAGE', displayName: '언어', description: '외국어, 한국어 등' },
  { value: 'SCIENCE', displayName: '과학', description: '물리, 화학, 생물 등' },
  { value: 'HISTORY', displayName: '역사', description: '세계사, 한국사 등' },
  { value: 'MATHEMATICS', displayName: '수학', description: '수학, 통계 등' },
  { value: 'ART', displayName: '예술', description: '미술, 음악, 문학 등' },

  // 비즈니스 & 커리어
  { value: 'STARTUP', displayName: '스타트업', description: '창업, 경영 등' },
  { value: 'MARKETING', displayName: '마케팅', description: '디지털 마케팅, 브랜딩 등' },
  { value: 'PROGRAMMING', displayName: '프로그래밍', description: '개발, 코딩 등' },
  { value: 'DESIGN', displayName: '디자인', description: 'UI/UX, 그래픽 디자인 등' },

  // 자기계발
  { value: 'PRODUCTIVITY', displayName: '생산성', description: '시간관리, 업무 효율 등' },
  { value: 'PSYCHOLOGY', displayName: '심리학', description: '인간 심리, 행동 등' },
  { value: 'FINANCE', displayName: '재테크', description: '투자, 저축 등' },
  { value: 'HEALTH', displayName: '건강', description: '운동, 식단, 정신건강 등' },

  // 라이프스타일
  { value: 'PARENTING', displayName: '육아', description: '자녀 교육, 육아 팁 등' },
  { value: 'COOKING', displayName: '요리', description: '레시피, 요리 기술 등' },
  { value: 'TRAVEL', displayName: '여행', description: '여행지, 여행 팁 등' },
  { value: 'HOBBY', displayName: '취미', description: '독서, 음악, 운동 등' },

  // 트렌드 & 인사이트
  { value: 'TREND', displayName: '트렌드', description: '최신 트렌드, 인사이트 등' },
  { value: 'OTHER', displayName: '기타', description: '기타 카테고리' },

  // 재미 & 가벼운 콘텐츠
  { value: 'FUN', displayName: '재미', description: '재미있거나 가벼운 콘텐츠' },
];

/**
 * 난이도 레벨 표시 정보
 */
export interface DifficultyLevelInfo {
  value: DifficultyLevel;
  displayName: string;
  description: string;
}

/**
 * 모든 난이도 레벨 목록
 */
export const DIFFICULTY_LEVELS: DifficultyLevelInfo[] = [
  { value: 'BEGINNER', displayName: '초급', description: '기초적인 내용' },
  { value: 'INTERMEDIATE', displayName: '중급', description: '중간 수준의 내용' },
  { value: 'ADVANCED', displayName: '고급', description: '고급 수준의 내용' },
];

/**
 * S3 Presigned Upload URL 요청
 * 백엔드: ContentUploadUrlRequest
 */
export interface ContentUploadUrlRequest {
  contentType: ContentType;
  fileName: string;
  fileSize: number;
  mimeType?: string;
}

/**
 * S3 Presigned Upload URL 응답
 * 백엔드: ContentUploadUrlResponse
 */
export interface ContentUploadUrlResponse {
  contentId: string;
  uploadUrl: string;
  expiresIn: number;
}

/**
 * 콘텐츠 생성 요청
 * 백엔드: ContentCreateRequest
 */
export interface ContentCreateRequest {
  contentId: string;
  title: string;
  description?: string;
  category: Category;
  tags?: string[];
  language?: string;
  photoUrls?: string[];
  thumbnailUrl: string;
  duration?: number;
  width: number;
  height: number;
}

/**
 * 콘텐츠 수정 요청
 * 백엔드: ContentUpdateRequest
 */
export interface ContentUpdateRequest {
  title?: string;
  description?: string;
  category?: Category;
  tags?: string[];
  language?: string;
  photoUrls?: string[];
}

/**
 * 콘텐츠 응답
 * 백엔드: ContentResponse
 */
export interface ContentResponse {
  id: string;
  creatorId: string;
  contentType: ContentType;
  url: string;
  photoUrls: string[] | null;
  thumbnailUrl: string;
  duration: number | null;
  width: number;
  height: number;
  status: ContentStatus;
  title: string;
  description: string | null;
  category: Category;
  tags: string[];
  language: string;
  interactions?: InteractionInfoResponse; // 인터랙션 정보 (인증된 사용자의 경우 isLiked, isSaved 포함)
  createdAt: string;
  updatedAt: string;
}

/**
 * 인터랙션 정보 응답
 * 백엔드: InteractionInfoResponse
 */
export interface InteractionInfoResponse {
  likeCount: number;
  commentCount: number;
  saveCount: number;
  shareCount: number;
  viewCount: number;
  isLiked: boolean;
  isSaved: boolean;
}

/**
 * 업로드 진행 상태
 */
export interface UploadProgress {
  contentId: string;
  fileName: string;
  progress: number; // 0-100
  status: 'uploading' | 'processing' | 'completed' | 'failed';
  error?: string;
}
