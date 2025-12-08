/**
 * 비디오 관련 유틸리티 함수
 */

/**
 * iOS URI에서 메타데이터 해시 제거
 *
 * iOS Photo Library에서 가져온 비디오 URI에는 plist 메타데이터가 포함될 수 있음
 * (base64 시그니처: '#YnBsaXN0')
 * expo-video 등에서 이를 처리하지 못하므로 제거 필요
 *
 * @param uri 원본 URI
 * @returns 메타데이터가 제거된 URI
 */
export const cleanIOSVideoUri = (uri: string): string => {
  return uri.replace(/#YnBsaXN0[A-Za-z0-9+/=]*$/, '');
};
