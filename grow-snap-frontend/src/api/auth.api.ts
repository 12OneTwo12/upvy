import apiClient from './client';
import { API_ENDPOINTS } from '@/constants/api';
import {
  LoginResponse,
  RefreshTokenRequest,
  RefreshTokenResponse,
  CheckNicknameRequest,
  CheckNicknameResponse,
  CreateProfileRequest,
  CreateProfileResponse,
  User,
} from '@/types/auth.types';

/**
 * Google OAuth 로그인
 * @param accessToken Google Access Token
 */
export const googleLogin = async (accessToken: string): Promise<LoginResponse> => {
  const response = await apiClient.post<LoginResponse>(API_ENDPOINTS.AUTH.GOOGLE_LOGIN, {
    accessToken,
  });
  return response.data;
};

/**
 * Refresh Token으로 Access Token 갱신
 */
export const refreshAccessToken = async (
  refreshToken: string
): Promise<RefreshTokenResponse> => {
  const response = await apiClient.post<RefreshTokenResponse>(
    API_ENDPOINTS.AUTH.REFRESH,
    { refreshToken } as RefreshTokenRequest
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
 * 닉네임 중복 확인
 */
export const checkNickname = async (nickname: string): Promise<CheckNicknameResponse> => {
  const response = await apiClient.post<CheckNicknameResponse>(
    API_ENDPOINTS.PROFILE.CHECK_NICKNAME,
    { nickname } as CheckNicknameRequest
  );
  return response.data;
};

/**
 * 프로필 생성 (첫 로그인)
 */
export const createProfile = async (
  data: CreateProfileRequest
): Promise<CreateProfileResponse> => {
  const response = await apiClient.post<CreateProfileResponse>(
    API_ENDPOINTS.PROFILE.ME,
    data
  );
  return response.data;
};

/**
 * 프로필 이미지 업로드
 */
export const uploadProfileImage = async (imageUri: string): Promise<{ imageUrl: string }> => {
  const formData = new FormData();
  formData.append('image', {
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
