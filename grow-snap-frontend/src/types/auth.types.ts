/**
 * 사용자 정보
 */
export interface User {
  id: string;
  email: string;
  provider: 'GOOGLE' | 'NAVER' | 'KAKAO';
  providerId: string;
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
  available: boolean;
  message?: string;
}

/**
 * 프로필 생성 요청
 */
export interface CreateProfileRequest {
  nickname: string;
  bio?: string;
}

/**
 * 프로필 생성 응답
 */
export interface CreateProfileResponse {
  profile: UserProfile;
}
