import React from 'react';
import { render } from '@testing-library/react-native';
import ProfileAvatar from '../ProfileAvatar';

describe('ProfileAvatar', () => {
  it('기본 프로필 아바타를 렌더링한다', () => {
    const { getByTestId } = render(<ProfileAvatar />);
    expect(getByTestId('profile-avatar')).toBeTruthy();
  });

  it('프로필 이미지 URL을 표시한다', () => {
    const imageUrl = 'https://example.com/profile.jpg';
    const { getByTestId } = render(<ProfileAvatar imageUrl={imageUrl} />);
    const image = getByTestId('profile-avatar-image');
    expect(image.props.source.uri).toBe(imageUrl);
  });

  it('이미지가 없을 때 기본 아이콘을 표시한다', () => {
    const { getByTestId } = render(<ProfileAvatar />);
    expect(getByTestId('profile-avatar-default')).toBeTruthy();
  });

  it('다양한 크기를 지원한다', () => {
    const sizes = ['small', 'medium', 'large', 'xlarge'] as const;
    const expectedSizes = { small: 40, medium: 60, large: 80, xlarge: 96 };

    sizes.forEach((size) => {
      const { getByTestId } = render(<ProfileAvatar size={size} />);
      const container = getByTestId('profile-avatar');
      expect(container.props.style).toEqual(
        expect.objectContaining({
          width: expectedSizes[size],
          height: expectedSizes[size],
        })
      );
    });
  });

  it('테두리 옵션을 적용한다', () => {
    const { getByTestId } = render(<ProfileAvatar showBorder />);
    const container = getByTestId('profile-avatar');
    expect(container.props.style).toEqual(
      expect.objectContaining({
        borderWidth: 2,
      })
    );
  });
});
