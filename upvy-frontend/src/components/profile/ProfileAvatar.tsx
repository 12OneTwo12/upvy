import React from 'react';
import { View, Image, TouchableOpacity, ViewStyle } from 'react-native';
import { theme } from '@/theme';
import { createStyleSheet } from '@/utils/styles';

interface ProfileAvatarProps {
  imageUrl?: string;
  size?: 'small' | 'medium' | 'large' | 'xlarge';
  onPress?: () => void;
  showBorder?: boolean;
  style?: ViewStyle;
}

const SIZES = {
  small: 40,
  medium: 60,
  large: 80,
  xlarge: 96,
};

const useStyles = createStyleSheet({
  container: {
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: theme.colors.background.secondary,
  },
  border: {
    borderWidth: 2,
    borderColor: theme.colors.primary[600],
    padding: 2,
  },
  image: {
    backgroundColor: theme.colors.background.secondary,
  },
  placeholder: {
    backgroundColor: theme.colors.gray[200],
  },
});

/**
 * 프로필 아바타 컴포넌트
 * 인스타그램 스타일의 프로필 이미지
 */
export default function ProfileAvatar({
  imageUrl,
  size = 'medium',
  onPress,
  showBorder = false,
  style,
}: ProfileAvatarProps) {
  const styles = useStyles();
  const avatarSize = SIZES[size];

  const content = (
    <View
      testID="profile-avatar"
      style={[
        styles.container,
        {
          width: avatarSize,
          height: avatarSize,
          borderRadius: avatarSize / 2,
        },
        showBorder && styles.border,
        style,
      ]}
    >
      {imageUrl ? (
        <Image
          testID="profile-avatar-image"
          source={{ uri: imageUrl }}
          style={[
            styles.image,
            {
              width: avatarSize - (showBorder ? 4 : 0),
              height: avatarSize - (showBorder ? 4 : 0),
              borderRadius: (avatarSize - (showBorder ? 4 : 0)) / 2,
            },
          ]}
          resizeMode="cover"
        />
      ) : (
        <View
          testID="profile-avatar-default"
          style={[
            styles.placeholder,
            {
              width: avatarSize - (showBorder ? 4 : 0),
              height: avatarSize - (showBorder ? 4 : 0),
              borderRadius: (avatarSize - (showBorder ? 4 : 0)) / 2,
            },
          ]}
        />
      )}
    </View>
  );

  if (onPress) {
    return (
      <TouchableOpacity onPress={onPress} activeOpacity={0.7}>
        {content}
      </TouchableOpacity>
    );
  }

  return content;
}
