/**
 * 신고 관련 타입 정의
 * 백엔드 API: grow-snap-backend/src/main/kotlin/me/onetwo/growsnap/domain/report/
 */

/**
 * 신고 대상 타입
 */
export type TargetType = 'content' | 'user';

/**
 * 신고 사유 타입
 * 백엔드 Enum: ReportType
 */
export type ReportType =
  | 'OFF_TOPIC'         // 주제에서 벗어남
  | 'SPAM'              // 스팸
  | 'INAPPROPRIATE_CONTENT'  // 부적절한 콘텐츠
  | 'COPYRIGHT'         // 저작권 침해
  | 'HARASSMENT'        // 괴롭힘
  | 'HATE_SPEECH'       // 혐오 발언
  | 'MISINFORMATION'    // 허위 정보
  | 'OTHER';            // 기타

/**
 * 신고 사유 정보
 */
export interface ReportReason {
  value: ReportType;
  label: string;
  description: string;
}

/**
 * 신고 요청
 * 백엔드 DTO: ReportRequest
 */
export interface ReportRequest {
  reportType: ReportType;
  description?: string;
}

/**
 * 신고 상태
 */
export type ReportStatus = 'PENDING' | 'REVIEWING' | 'RESOLVED' | 'REJECTED';

/**
 * 신고 응답
 * 백엔드 DTO: ReportResponse
 */
export interface ReportResponse {
  id: number;
  reporterId: string;
  targetType: TargetType;
  targetId: string;
  reportType: ReportType;
  description: string | null;
  status: ReportStatus;
  createdAt: string;
}

/**
 * 신고 사유 목록
 */
export const REPORT_REASONS: ReportReason[] = [
  {
    value: 'OFF_TOPIC',
    label: '주제에서 벗어남',
    description: '공부 콘텐츠에서 벗어나거나 주제와 관련 없는 내용',
  },
  {
    value: 'SPAM',
    label: '스팸',
    description: '광고, 홍보, 반복적인 무의미한 콘텐츠',
  },
  {
    value: 'INAPPROPRIATE_CONTENT',
    label: '부적절한 콘텐츠',
    description: '폭력적이거나 성적인 콘텐츠',
  },
  {
    value: 'HARASSMENT',
    label: '괴롭힘',
    description: '특정인을 대상으로 한 괴롭힘이나 명예훼손',
  },
  {
    value: 'HATE_SPEECH',
    label: '혐오 발언',
    description: '특정 집단에 대한 차별이나 혐오 표현',
  },
  {
    value: 'MISINFORMATION',
    label: '허위 정보',
    description: '의도적으로 거짓 정보를 유포하는 콘텐츠',
  },
  {
    value: 'COPYRIGHT',
    label: '저작권 침해',
    description: '타인의 저작물을 무단으로 사용한 콘텐츠',
  },
  {
    value: 'OTHER',
    label: '기타',
    description: '위 카테고리에 해당하지 않는 기타 사유',
  },
];
