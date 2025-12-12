import apiClient from './client';
import { API_ENDPOINTS } from '@/constants/api';
import {
  RefreshTokenResponse,
  CheckNicknameResponse,
  CreateProfileRequest,
  CreateProfileResponse,
  User,
  UserProfile,
  FollowStats,
  CheckFollowResponse,
  FollowResponse,
  EmailSignupRequest,
  EmailSigninRequest,
  EmailVerifyCodeRequest,
  EmailVerifyResponse,
  ResendVerificationCodeRequest,
  ChangePasswordRequest,
  ResetPasswordRequest,
  ResetPasswordVerifyCodeRequest,
  ResetPasswordConfirmRequest,
} from '@/types/auth.types';

/**
 * Refresh Token으로 Access Token 갱신
 *
 * Note: OAuth2 로그인은 백엔드 Custom Tabs 방식 사용 (useGoogleAuth 참고)
 */
export const refreshAccessToken = async (
  refreshToken: string
): Promise<RefreshTokenResponse> => {
  const response = await apiClient.post<RefreshTokenResponse>(
    API_ENDPOINTS.AUTH.REFRESH,
    { refreshToken }
  );
  return response.data;
};

/**
 * 로그아웃
 */
export const logout = async (): Promise<void> => {
  await apiClient.post(API_ENDPOINTS.AUTH.LOGOUT);
};

/**
 * 현재 사용자 정보 조회
 */
export const getCurrentUser = async (): Promise<User> => {
  const response = await apiClient.get<User>(API_ENDPOINTS.USER.ME);
  return response.data;
};

/**
 * 내 프로필 조회
 */
export const getMyProfile = async (): Promise<UserProfile> => {
  const response = await apiClient.get<UserProfile>(API_ENDPOINTS.PROFILE.ME);
  return response.data;
};

/**
 * 닉네임 중복 확인
 */
export const checkNickname = async (nickname: string): Promise<CheckNicknameResponse> => {
  const response = await apiClient.get<CheckNicknameResponse>(
    API_ENDPOINTS.PROFILE.CHECK_NICKNAME(nickname)
  );
  return response.data;
};

/**
 * 프로필 생성
 * 회원가입 후 최초 1회 프로필을 생성합니다.
 * 백엔드에서 프로필은 자동 생성되지 않으며, 사용자가 직접 생성해야 합니다.
 */
export const createProfile = async (
  data: CreateProfileRequest
): Promise<CreateProfileResponse> => {
  const response = await apiClient.post<CreateProfileResponse>(
    API_ENDPOINTS.PROFILE.UPDATE,  // POST /api/v1/profiles
    data
  );
  return response.data;
};

/**
 * 프로필 이미지 업로드
 */
export const uploadProfileImage = async (imageUri: string): Promise<{ imageUrl: string }> => {
  const formData = new FormData();
  // React Native의 FormData는 웹 표준과 다른 파일 객체 형식을 사용하므로 타입 단언이 필요합니다.
  // 백엔드는 'file' 파라미터명을 사용합니다.
  formData.append('file', {
    uri: imageUri,
    type: 'image/jpeg',
    name: 'profile.jpg',
  } as any);

  const response = await apiClient.post<{ imageUrl: string }>(
    API_ENDPOINTS.PROFILE.UPLOAD_IMAGE,
    formData,
    {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    }
  );
  return response.data;
};

/**
 * 다른 사용자 프로필 조회 (userId로)
 */
export const getProfileByUserId = async (userId: string): Promise<UserProfile> => {
  const response = await apiClient.get<UserProfile>(
    API_ENDPOINTS.PROFILE.BY_USER_ID(userId)
  );
  return response.data;
};

/**
 * 다른 사용자 프로필 조회 (nickname으로)
 */
export const getProfileByNickname = async (nickname: string): Promise<UserProfile> => {
  const response = await apiClient.get<UserProfile>(
    API_ENDPOINTS.PROFILE.BY_NICKNAME(nickname)
  );
  return response.data;
};

/**
 * 사용자 팔로우
 */
export const followUser = async (userId: string): Promise<FollowResponse> => {
  const response = await apiClient.post<FollowResponse>(
    API_ENDPOINTS.FOLLOW.FOLLOW(userId)
  );
  return response.data;
};

/**
 * 사용자 언팔로우
 */
export const unfollowUser = async (userId: string): Promise<void> => {
  await apiClient.delete(API_ENDPOINTS.FOLLOW.UNFOLLOW(userId));
};

/**
 * 팔로우 여부 확인
 */
export const checkFollowing = async (userId: string): Promise<CheckFollowResponse> => {
  const response = await apiClient.get<CheckFollowResponse>(
    API_ENDPOINTS.FOLLOW.CHECK(userId)
  );
  return response.data;
};

/**
 * 내 팔로우 통계 조회
 */
export const getMyFollowStats = async (): Promise<FollowStats> => {
  const response = await apiClient.get<FollowStats>(API_ENDPOINTS.FOLLOW.STATS_ME);
  return response.data;
};

/**
 * 특정 사용자 팔로우 통계 조회
 */
export const getFollowStats = async (userId: string): Promise<FollowStats> => {
  const response = await apiClient.get<FollowStats>(
    API_ENDPOINTS.FOLLOW.STATS(userId)
  );
  return response.data;
};

/**
 * 팔로워 목록 조회
 */
export const getFollowers = async (userId: string): Promise<UserProfile[]> => {
  const response = await apiClient.get<UserProfile[]>(
    API_ENDPOINTS.FOLLOW.FOLLOWERS(userId)
  );
  return response.data;
};

/**
 * 팔로잉 목록 조회
 */
export const getFollowing = async (userId: string): Promise<UserProfile[]> => {
  const response = await apiClient.get<UserProfile[]>(
    API_ENDPOINTS.FOLLOW.FOLLOWING(userId)
  );
  return response.data;
};

/**
 * 이메일 회원가입
 * 백엔드: POST /api/v1/auth/email/signup
 *
 * @param data 회원가입 정보 (이메일, 비밀번호, 이름, 언어)
 * @returns 201 Created (Void)
 */
export const emailSignup = async (data: EmailSignupRequest): Promise<void> => {
  await apiClient.post(API_ENDPOINTS.AUTH.EMAIL_SIGNUP, data);
};

/**
 * 이메일 인증 코드 검증
 * 백엔드: POST /api/v1/auth/email/verify-code
 *
 * @param data 이메일과 6자리 인증 코드
 * @returns JWT 토큰 및 사용자 정보
 */
export const verifyEmailCode = async (
  data: EmailVerifyCodeRequest
): Promise<EmailVerifyResponse> => {
  const response = await apiClient.post<EmailVerifyResponse>(
    API_ENDPOINTS.AUTH.EMAIL_VERIFY_CODE,
    data
  );
  return response.data;
};

/**
 * 인증 코드 재전송
 * 백엔드: POST /api/v1/auth/email/resend-code
 *
 * @param data 이메일과 언어 설정
 * @returns 204 No Content (Void)
 */
export const resendVerificationCode = async (
  data: ResendVerificationCodeRequest
): Promise<void> => {
  await apiClient.post(API_ENDPOINTS.AUTH.EMAIL_RESEND_CODE, data);
};

/**
 * 이메일 로그인
 * 백엔드: POST /api/v1/auth/email/signin
 *
 * @param data 이메일과 비밀번호
 * @returns JWT 토큰 및 사용자 정보
 */
export const emailSignin = async (
  data: EmailSigninRequest
): Promise<EmailVerifyResponse> => {
  const response = await apiClient.post<EmailVerifyResponse>(
    API_ENDPOINTS.AUTH.EMAIL_SIGNIN,
    data
  );
  return response.data;
};

/**
 * 비밀번호 변경
 * 백엔드: POST /api/v1/auth/password/change (인증 필요)
 *
 * @param data 현재 비밀번호와 새 비밀번호
 * @returns 204 No Content (Void)
 */
export const changePassword = async (data: ChangePasswordRequest): Promise<void> => {
  await apiClient.post(API_ENDPOINTS.AUTH.PASSWORD_CHANGE, data);
};

/**
 * 비밀번호 재설정 요청
 * 백엔드: POST /api/v1/auth/password/reset/request
 *
 * @param data 이메일과 언어 설정
 * @returns 204 No Content (Void)
 */
export const resetPasswordRequest = async (
  data: ResetPasswordRequest
): Promise<void> => {
  await apiClient.post(API_ENDPOINTS.AUTH.PASSWORD_RESET_REQUEST, data);
};

/**
 * 비밀번호 재설정 코드 검증
 * 백엔드: POST /api/v1/auth/password/reset/verify-code
 *
 * @param data 이메일과 인증 코드
 * @returns 204 No Content (Void)
 */
export const resetPasswordVerifyCode = async (
  data: ResetPasswordVerifyCodeRequest
): Promise<void> => {
  await apiClient.post(API_ENDPOINTS.AUTH.PASSWORD_RESET_VERIFY_CODE, data);
};

/**
 * 비밀번호 재설정 확정
 * 백엔드: POST /api/v1/auth/password/reset/confirm
 *
 * @param data 이메일, 인증 코드, 새 비밀번호
 * @returns 204 No Content (Void)
 */
export const resetPasswordConfirm = async (
  data: ResetPasswordConfirmRequest
): Promise<void> => {
  await apiClient.post(API_ENDPOINTS.AUTH.PASSWORD_RESET_CONFIRM, data);
};
