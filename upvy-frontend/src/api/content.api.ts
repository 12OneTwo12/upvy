/**
 * 콘텐츠 API 클라이언트
 *
 * 백엔드 ContentController의 API를 호출합니다.
 * 참조: upvy-backend/.../content/controller/ContentController.kt
 */

import apiClient from '@/api/client';
import { API_ENDPOINTS, API_UPLOAD_TIMEOUT } from '@/constants/api';
import type {
  ContentUploadUrlRequest,
  ContentUploadUrlResponse,
  ContentCreateRequest,
  ContentUpdateRequest,
  ContentResponse,
} from '@/types/content.types';
import type { CursorPageResponse, CursorPageParams } from '@/types/pagination.types';

/**
 * S3 Presigned Upload URL을 생성합니다.
 *
 * 백엔드: POST /api/v1/contents/upload-url
 *
 * @param request 업로드 URL 요청
 * @returns Presigned URL 정보
 */
export const generateUploadUrl = async (
  request: ContentUploadUrlRequest
): Promise<ContentUploadUrlResponse> => {
  const { data } = await apiClient.post<ContentUploadUrlResponse>(
    API_ENDPOINTS.CONTENT.UPLOAD_URL,
    request,
    { timeout: API_UPLOAD_TIMEOUT } // 업로드용 긴 타임아웃 (120초)
  );
  return data;
};

/**
 * S3에 파일을 직접 업로드합니다.
 *
 * Presigned URL을 사용하여 S3에 직접 파일을 업로드합니다.
 *
 * @param uploadUrl S3 Presigned URL
 * @param file 업로드할 파일
 * @param onProgress 업로드 진행 콜백
 */
export const uploadFileToS3 = async (
  uploadUrl: string,
  file: File | Blob,
  onProgress?: (progress: number) => void
): Promise<void> => {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();

    // 타임아웃 설정 (120초)
    xhr.timeout = API_UPLOAD_TIMEOUT;

    xhr.upload.addEventListener('progress', (event) => {
      if (event.lengthComputable && onProgress) {
        const progress = Math.round((event.loaded / event.total) * 100);
        onProgress(progress);
      }
    });

    xhr.addEventListener('load', () => {
      if (xhr.status >= 200 && xhr.status < 300) {
        resolve();
      } else {
        reject(new Error(`Upload failed with status ${xhr.status}`));
      }
    });

    xhr.addEventListener('error', () => {
      reject(new Error('Upload failed'));
    });

    xhr.addEventListener('timeout', () => {
      reject(new Error('Upload timeout - please check your network connection'));
    });

    xhr.open('PUT', uploadUrl);
    // Presigned URL 서명 검증을 위해 Content-Type만 설정
    // Content-Length는 브라우저/앱이 자동 설정
    // x-amz-acl은 버킷이 ACL을 비활성화했으므로 제거
    xhr.setRequestHeader('Content-Type', file.type);
    xhr.send(file);
  });
};

/**
 * 콘텐츠를 생성합니다.
 *
 * S3 업로드 완료 후 콘텐츠 메타데이터를 등록합니다.
 *
 * 백엔드: POST /api/v1/contents
 *
 * @param request 콘텐츠 생성 요청
 * @returns 생성된 콘텐츠 정보
 */
export const createContent = async (
  request: ContentCreateRequest
): Promise<ContentResponse> => {
  const { data } = await apiClient.post<ContentResponse>(
    API_ENDPOINTS.CONTENT.CREATE,
    request,
    { timeout: API_UPLOAD_TIMEOUT } // 업로드 관련이므로 긴 타임아웃 적용
  );
  return data;
};

/**
 * 콘텐츠를 조회합니다.
 *
 * 백엔드: GET /api/v1/contents/{contentId}
 *
 * @param contentId 콘텐츠 ID
 * @returns 콘텐츠 정보
 */
export const getContent = async (contentId: string): Promise<ContentResponse> => {
  const { data } = await apiClient.get<ContentResponse>(
    API_ENDPOINTS.CONTENT.GET(contentId)
  );
  return data;
};

/**
 * 내 콘텐츠 목록을 커서 기반 페이징으로 조회합니다.
 *
 * 백엔드: GET /api/v1/contents/me?cursor={cursor}&limit={limit}
 *
 * @param params 커서 페이지 파라미터 (cursor, limit)
 * @returns 커서 페이지 응답
 */
export const getMyContents = async (
  params?: CursorPageParams
): Promise<CursorPageResponse<ContentResponse>> => {
  const { data } = await apiClient.get<CursorPageResponse<ContentResponse>>(
    API_ENDPOINTS.CONTENT.MY_CONTENTS,
    { params }
  );
  return data;
};

/**
 * 콘텐츠를 수정합니다.
 *
 * 백엔드: PATCH /api/v1/contents/{contentId}
 *
 * @param contentId 콘텐츠 ID
 * @param request 수정 요청
 * @returns 수정된 콘텐츠 정보
 */
export const updateContent = async (
  contentId: string,
  request: ContentUpdateRequest
): Promise<ContentResponse> => {
  const { data } = await apiClient.patch<ContentResponse>(
    API_ENDPOINTS.CONTENT.UPDATE(contentId),
    request
  );
  return data;
};

/**
 * 콘텐츠를 삭제합니다 (Soft Delete).
 *
 * 백엔드: DELETE /api/v1/contents/{contentId}
 *
 * @param contentId 콘텐츠 ID
 */
export const deleteContent = async (contentId: string): Promise<void> => {
  await apiClient.delete(API_ENDPOINTS.CONTENT.DELETE(contentId));
};

/**
 * 사용자의 콘텐츠 목록을 커서 기반 페이징으로 조회합니다.
 *
 * 백엔드: GET /api/v1/profiles/{userId}/contents?cursor={cursor}&limit={limit}
 *
 * @param userId 사용자 ID
 * @param params 커서 페이지 파라미터 (cursor, limit)
 * @returns 커서 페이지 응답
 */
export const getUserContents = async (
  userId: string,
  params?: CursorPageParams
): Promise<CursorPageResponse<ContentResponse>> => {
  const { data } = await apiClient.get<CursorPageResponse<ContentResponse>>(
    `/profiles/${userId}/contents`,
    { params }
  );
  return data;
};
