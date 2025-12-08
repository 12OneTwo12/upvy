import apiClient from './client';
import { API_ENDPOINTS } from '@/constants/api';

/**
 * 회원 탈퇴 (소프트 삭제)
 */
export const deleteUser = async (): Promise<void> => {
  await apiClient.delete(API_ENDPOINTS.USER.ME);
};
