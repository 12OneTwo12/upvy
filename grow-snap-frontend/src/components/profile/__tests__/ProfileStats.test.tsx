import React from 'react';
import { render, fireEvent } from '@testing-library/react-native';
import ProfileStats from '../ProfileStats';

describe('ProfileStats', () => {
  const mockFollowersPress = jest.fn();
  const mockFollowingPress = jest.fn();

  beforeEach(() => {
    mockFollowersPress.mockClear();
    mockFollowingPress.mockClear();
  });

  it('팔로워와 팔로잉 수를 렌더링한다', () => {
    const { getByText } = render(
      <ProfileStats
        followersCount={100}
        followingCount={50}
        onFollowersPress={mockFollowersPress}
        onFollowingPress={mockFollowingPress}
      />
    );

    expect(getByText('100')).toBeTruthy();
    expect(getByText('50')).toBeTruthy();
    expect(getByText('팔로워')).toBeTruthy();
    expect(getByText('팔로잉')).toBeTruthy();
  });

  it('큰 숫자를 K 포맷으로 표시한다', () => {
    const { getByText } = render(
      <ProfileStats
        followersCount={1500}
        followingCount={2000}
        onFollowersPress={mockFollowersPress}
        onFollowingPress={mockFollowingPress}
      />
    );

    expect(getByText('1.5K')).toBeTruthy();
    expect(getByText('2.0K')).toBeTruthy();
  });

  it('매우 큰 숫자를 M 포맷으로 표시한다', () => {
    const { getByText } = render(
      <ProfileStats
        followersCount={1500000}
        followingCount={2000000}
        onFollowersPress={mockFollowersPress}
        onFollowingPress={mockFollowingPress}
      />
    );

    expect(getByText('1.5M')).toBeTruthy();
    expect(getByText('2.0M')).toBeTruthy();
  });

  it('팔로워 클릭 이벤트를 처리한다', () => {
    const { getByText } = render(
      <ProfileStats
        followersCount={100}
        followingCount={50}
        onFollowersPress={mockFollowersPress}
        onFollowingPress={mockFollowingPress}
      />
    );

    fireEvent.press(getByText('팔로워'));
    expect(mockFollowersPress).toHaveBeenCalledTimes(1);
  });

  it('팔로잉 클릭 이벤트를 처리한다', () => {
    const { getByText } = render(
      <ProfileStats
        followersCount={100}
        followingCount={50}
        onFollowersPress={mockFollowersPress}
        onFollowingPress={mockFollowingPress}
      />
    );

    fireEvent.press(getByText('팔로잉'));
    expect(mockFollowingPress).toHaveBeenCalledTimes(1);
  });
});
