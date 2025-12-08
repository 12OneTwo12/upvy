import React from 'react';
import { render, fireEvent } from '@testing-library/react-native';
import FollowButton from '../FollowButton';

describe('FollowButton', () => {
  const mockOnPress = jest.fn();

  beforeEach(() => {
    mockOnPress.mockClear();
  });

  it('팔로우 버튼을 렌더링한다', () => {
    const { getByText } = render(
      <FollowButton isFollowing={false} onPress={mockOnPress} />
    );

    expect(getByText('팔로우')).toBeTruthy();
  });

  it('언팔로우 버튼을 렌더링한다', () => {
    const { getByText } = render(
      <FollowButton isFollowing={true} onPress={mockOnPress} />
    );

    expect(getByText('팔로잉')).toBeTruthy();
  });

  it('버튼 클릭 이벤트를 처리한다', () => {
    const { getByText } = render(
      <FollowButton isFollowing={false} onPress={mockOnPress} />
    );

    fireEvent.press(getByText('팔로우'));
    expect(mockOnPress).toHaveBeenCalledTimes(1);
  });

  it('로딩 상태일 때 ActivityIndicator를 표시한다', () => {
    const { getByTestId, queryByText } = render(
      <FollowButton isFollowing={false} onPress={mockOnPress} loading />
    );

    expect(getByTestId('follow-button-loading')).toBeTruthy();
    expect(queryByText('팔로우')).toBeNull();
  });

  it('disabled 상태일 때 클릭 이벤트가 발생하지 않는다', () => {
    const { getByText } = render(
      <FollowButton isFollowing={false} onPress={mockOnPress} disabled />
    );

    fireEvent.press(getByText('팔로우'));
    expect(mockOnPress).not.toHaveBeenCalled();
  });

  it('로딩 중일 때 버튼이 비활성화된다', () => {
    const { getByTestId } = render(
      <FollowButton isFollowing={false} onPress={mockOnPress} loading />
    );

    const button = getByTestId('follow-button');
    fireEvent.press(button);
    expect(mockOnPress).not.toHaveBeenCalled();
  });
});
