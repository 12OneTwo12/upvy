import axios from 'axios';
import {
  getProfileByUserId,
  getProfileByNickname,
  followUser,
  unfollowUser,
  checkFollowing,
  getFollowers,
  getFollowing,
  getMyFollowStats,
  getFollowStats,
} from '../auth.api';
import { UserProfile, CheckFollowResponse, FollowResponse } from '@/types/auth.types';

// Mock axios
jest.mock('axios');
const mockedAxios = axios as jest.Mocked<typeof axios>;

// Mock apiClient
jest.mock('../client', () => ({
  __esModule: true,
  default: {
    get: jest.fn(),
    post: jest.fn(),
    delete: jest.fn(),
    patch: jest.fn(),
  },
}));

import apiClient from '../client';
const mockedApiClient = apiClient as jest.Mocked<typeof apiClient>;

describe('auth.api - Profile APIs', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  const mockProfile: UserProfile = {
    userId: 'user-123',
    nickname: 'testuser',
    bio: 'Test bio',
    profileImageUrl: 'https://example.com/profile.jpg',
    interests: ['성장', '운동'],
    followerCount: 100,
    followingCount: 50,
    isCreator: false,
    createdAt: '2025-01-01T00:00:00Z',
    updatedAt: '2025-01-01T00:00:00Z',
  };

  describe('getProfileByUserId', () => {
    it('사용자 ID로 프로필을 조회한다', async () => {
      mockedApiClient.get.mockResolvedValue({ data: mockProfile });

      const result = await getProfileByUserId('user-123');

      expect(mockedApiClient.get).toHaveBeenCalledWith('/api/v1/profiles/user-id/user-123');
      expect(result).toEqual(mockProfile);
    });

    it('API 에러 시 에러를 던진다', async () => {
      const error = new Error('Network error');
      mockedApiClient.get.mockRejectedValue(error);

      await expect(getProfileByUserId('user-123')).rejects.toThrow('Network error');
    });
  });

  describe('getProfileByNickname', () => {
    it('닉네임으로 프로필을 조회한다', async () => {
      mockedApiClient.get.mockResolvedValue({ data: mockProfile });

      const result = await getProfileByNickname('testuser');

      expect(mockedApiClient.get).toHaveBeenCalledWith('/api/v1/profiles/nickname/testuser');
      expect(result).toEqual(mockProfile);
    });
  });
});

describe('auth.api - Follow APIs', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  describe('followUser', () => {
    it('사용자를 팔로우한다', async () => {
      const mockFollowResponse: FollowResponse = {
        id: 1,
        followerId: 'user-1',
        followingId: 'user-2',
        createdAt: '2025-01-01T00:00:00Z',
      };

      mockedApiClient.post.mockResolvedValue({ data: mockFollowResponse });

      const result = await followUser('user-2');

      expect(mockedApiClient.post).toHaveBeenCalledWith('/api/v1/follow/user-2');
      expect(result).toEqual(mockFollowResponse);
    });
  });

  describe('unfollowUser', () => {
    it('사용자를 언팔로우한다', async () => {
      mockedApiClient.delete.mockResolvedValue({ data: undefined });

      await unfollowUser('user-2');

      expect(mockedApiClient.delete).toHaveBeenCalledWith('/api/v1/follow/user-2');
    });
  });

  describe('checkFollowing', () => {
    it('팔로우 여부를 확인한다', async () => {
      const mockCheckResponse: CheckFollowResponse = {
        followerId: 'user-1',
        followingId: 'user-2',
        isFollowing: true,
      };

      mockedApiClient.get.mockResolvedValue({ data: mockCheckResponse });

      const result = await checkFollowing('user-2');

      expect(mockedApiClient.get).toHaveBeenCalledWith('/api/v1/follow/check/user-2');
      expect(result).toEqual(mockCheckResponse);
    });

    it('팔로우하지 않은 사용자의 경우 isFollowing이 false이다', async () => {
      const mockCheckResponse: CheckFollowResponse = {
        followerId: 'user-1',
        followingId: 'user-2',
        isFollowing: false,
      };

      mockedApiClient.get.mockResolvedValue({ data: mockCheckResponse });

      const result = await checkFollowing('user-2');

      expect(result.isFollowing).toBe(false);
    });
  });

  describe('getFollowers', () => {
    it('팔로워 목록을 조회한다', async () => {
      const mockFollowers: UserProfile[] = [
        {
          userId: 'user-1',
          nickname: 'follower1',
          bio: 'Follower 1',
          profileImageUrl: 'https://example.com/1.jpg',
          interests: ['성장'],
          followerCount: 10,
          followingCount: 20,
          isCreator: false,
          createdAt: '2025-01-01T00:00:00Z',
          updatedAt: '2025-01-01T00:00:00Z',
        },
        {
          userId: 'user-2',
          nickname: 'follower2',
          bio: 'Follower 2',
          profileImageUrl: 'https://example.com/2.jpg',
          interests: ['운동'],
          followerCount: 15,
          followingCount: 25,
          isCreator: true,
          createdAt: '2025-01-01T00:00:00Z',
          updatedAt: '2025-01-01T00:00:00Z',
        },
      ];

      mockedApiClient.get.mockResolvedValue({ data: mockFollowers });

      const result = await getFollowers('user-123');

      expect(mockedApiClient.get).toHaveBeenCalledWith('/api/v1/follow/user-123/followers');
      expect(result).toEqual(mockFollowers);
      expect(result).toHaveLength(2);
    });
  });

  describe('getFollowing', () => {
    it('팔로잉 목록을 조회한다', async () => {
      const mockFollowing: UserProfile[] = [
        {
          userId: 'user-3',
          nickname: 'following1',
          bio: 'Following 1',
          profileImageUrl: 'https://example.com/3.jpg',
          interests: ['독서'],
          followerCount: 30,
          followingCount: 40,
          isCreator: false,
          createdAt: '2025-01-01T00:00:00Z',
          updatedAt: '2025-01-01T00:00:00Z',
        },
      ];

      mockedApiClient.get.mockResolvedValue({ data: mockFollowing });

      const result = await getFollowing('user-123');

      expect(mockedApiClient.get).toHaveBeenCalledWith('/api/v1/follow/user-123/following');
      expect(result).toEqual(mockFollowing);
      expect(result).toHaveLength(1);
    });
  });

  describe('getMyFollowStats', () => {
    it('내 팔로우 통계를 조회한다', async () => {
      const mockStats = {
        followerCount: 100,
        followingCount: 50,
      };

      mockedApiClient.get.mockResolvedValue({ data: mockStats });

      const result = await getMyFollowStats();

      expect(mockedApiClient.get).toHaveBeenCalledWith('/api/v1/follow/stats/me');
      expect(result).toEqual(mockStats);
    });
  });

  describe('getFollowStats', () => {
    it('특정 사용자의 팔로우 통계를 조회한다', async () => {
      const mockStats = {
        followerCount: 200,
        followingCount: 150,
      };

      mockedApiClient.get.mockResolvedValue({ data: mockStats });

      const result = await getFollowStats('user-123');

      expect(mockedApiClient.get).toHaveBeenCalledWith('/api/v1/follow/stats/user-123');
      expect(result).toEqual(mockStats);
    });
  });
});
