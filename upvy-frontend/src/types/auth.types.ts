/**
 * 사용자 정보
 */
export interface User {
  id: string;
  email: string;
  provider: 'EMAIL' | 'GOOGLE' | 'APPLE' | 'NAVER' | 'KAKAO';
  role: 'USER' | 'CREATOR' | 'ADMIN';
  createdAt: string;
  updatedAt: string;
}

/**
 * 사용자 프로필
 */
export interface UserProfile {
  id: number;
  userId: string;
  nickname: string;
  profileImageUrl?: string;
  bio?: string;
  followerCount: number;
  followingCount: number;
  createdAt: string;
  updatedAt: string;
}

/**
 * 로그인 응답
 */
export interface LoginResponse {
  accessToken: string;
  refreshToken: string;
  user: User;
  profile?: UserProfile;
}

/**
 * Refresh Token 요청
 */
export interface RefreshTokenRequest {
  refreshToken: string;
}

/**
 * Refresh Token 응답
 */
export interface RefreshTokenResponse {
  accessToken: string;
}

/**
 * 닉네임 중복 확인 요청
 */
export interface CheckNicknameRequest {
  nickname: string;
}

/**
 * 닉네임 중복 확인 응답
 */
export interface CheckNicknameResponse {
  nickname: string;
  isDuplicated: boolean;
}

/**
 * 프로필 생성 요청
 */
export interface CreateProfileRequest {
  nickname: string;
  profileImageUrl?: string;
  bio?: string;
}

/**
 * 프로필 생성 응답 (백엔드는 UserProfileResponse를 직접 반환)
 */
export type CreateProfileResponse = UserProfile;

/**
 * 팔로우 통계
 */
export interface FollowStats {
  followerCount: number;
  followingCount: number;
}

/**
 * 팔로우 체크 응답
 */
export interface CheckFollowResponse {
  followerId: string;
  followingId: string;
  isFollowing: boolean;
}

/**
 * 팔로우 응답
 */
export interface FollowResponse {
  id: number;
  followerId: string;
  followingId: string;
  createdAt: string;
}

/**
 * 이메일 회원가입 요청
 * 백엔드: EmailSignupRequest
 */
export interface EmailSignupRequest {
  email: string;
  password: string;
  language?: string;
}

/**
 * 이메일 로그인 요청
 * 백엔드: EmailSigninRequest
 */
export interface EmailSigninRequest {
  email: string;
  password: string;
}

/**
 * 이메일 인증 코드 검증 요청
 * 백엔드: EmailVerifyCodeRequest
 */
export interface EmailVerifyCodeRequest {
  email: string;
  code: string;
}

/**
 * 인증 코드 재전송 요청
 * 백엔드: ResendVerificationCodeRequest
 */
export interface ResendVerificationCodeRequest {
  email: string;
  language?: string;
}

/**
 * 이메일 인증 응답
 * 백엔드: EmailVerifyResponse
 */
export interface EmailVerifyResponse {
  accessToken: string;
  refreshToken: string;
  userId: string;
  email: string;
}

/**
 * 비밀번호 변경 요청
 * 백엔드: ChangePasswordRequest
 */
export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}

/**
 * 비밀번호 재설정 요청
 * 백엔드: ResetPasswordRequest
 */
export interface ResetPasswordRequest {
  email: string;
  language?: string;
}

/**
 * 비밀번호 재설정 코드 검증 요청
 * 백엔드: ResetPasswordVerifyCodeRequest
 */
export interface ResetPasswordVerifyCodeRequest {
  email: string;
  code: string;
}

/**
 * 비밀번호 재설정 확정 요청
 * 백엔드: ResetPasswordConfirmRequest
 */
export interface ResetPasswordConfirmRequest {
  email: string;
  code: string;
  newPassword: string;
}

/**
 * 약관 동의 요청
 * 백엔드: TermsAgreementRequest
 */
export interface TermsAgreementRequest {
  serviceTermsAgreed: boolean;
  privacyPolicyAgreed: boolean;
  communityGuidelinesAgreed: boolean;
  marketingAgreed: boolean;
}

/**
 * 약관 동의 응답
 * 백엔드: TermsAgreementResponse
 */
export interface TermsAgreementResponse {
  userId: string;
  serviceTermsAgreed: boolean;
  serviceTermsVersion: string | null;
  serviceTermsAgreedAt: string | null;
  privacyPolicyAgreed: boolean;
  privacyPolicyVersion: string | null;
  privacyPolicyAgreedAt: string | null;
  communityGuidelinesAgreed: boolean;
  communityGuidelinesVersion: string | null;
  communityGuidelinesAgreedAt: string | null;
  marketingAgreed: boolean;
  marketingVersion: string | null;
  marketingAgreedAt: string | null;
  isAllRequiredAgreed: boolean;
}
