import React from 'react';
import { render } from '@testing-library/react-native';
import ProfileHeader from '../ProfileHeader';
import { UserProfile } from '@/types/auth.types';

describe('ProfileHeader', () => {
  const mockProfile: UserProfile = {
    id: 1,
    userId: 'user-123',
    nickname: 'testuser',
    bio: 'Test bio',
    profileImageUrl: 'https://example.com/profile.jpg',
    followerCount: 100,
    followingCount: 50,
    contentCount: 25,
    createdAt: '2025-01-01T00:00:00Z',
    updatedAt: '2025-01-01T00:00:00Z',
  };

  it('프로필 정보를 렌더링한다', () => {
    const { getByText } = render(<ProfileHeader profile={mockProfile} />);

    expect(getByText('testuser')).toBeTruthy();
    expect(getByText('Test bio')).toBeTruthy();
  });

  it('팔로워/팔로잉 통계를 표시한다', () => {
    const { getByText } = render(
      <ProfileHeader profile={mockProfile} showStats />
    );

    expect(getByText('100')).toBeTruthy();
    expect(getByText('50')).toBeTruthy();
  });

  it('showStats가 false일 때 통계를 숨긴다', () => {
    const { queryByText } = render(
      <ProfileHeader profile={mockProfile} showStats={false} />
    );

    expect(queryByText('팔로워')).toBeNull();
    expect(queryByText('팔로잉')).toBeNull();
  });

  it('bio가 없을 때 bio를 표시하지 않는다', () => {
    const profileWithoutBio = { ...mockProfile, bio: undefined };
    const { queryByText } = render(
      <ProfileHeader profile={profileWithoutBio} />
    );

    expect(queryByText('Test bio')).toBeNull();
  });
});
