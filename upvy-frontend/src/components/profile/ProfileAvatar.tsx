import React from 'react';
import { View, Image, TouchableOpacity, ViewStyle, Text } from 'react-native';
import { theme } from '@/theme';
import { createStyleSheet } from '@/utils/styles';

interface ProfileAvatarProps {
  imageUrl?: string;
  nickname?: string;
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

const FONT_SIZES = {
  small: 16,
  medium: 20,
  large: 24,
  xlarge: 30,
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
  firstLetterContainer: {
    backgroundColor: theme.colors.primary[100],
    alignItems: 'center',
    justifyContent: 'center',
  },
  firstLetterText: {
    fontWeight: theme.typography.fontWeight.bold,
    color: theme.colors.primary[500],
  },
});

/**
 * 프로필 아바타 컴포넌트
 * 인스타그램 스타일의 프로필 이미지
 */
export default function ProfileAvatar({
  imageUrl,
  nickname,
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
      ) : nickname ? (
        <View
          testID="profile-avatar-letter"
          style={[
            styles.firstLetterContainer,
            {
              width: avatarSize - (showBorder ? 4 : 0),
              height: avatarSize - (showBorder ? 4 : 0),
              borderRadius: (avatarSize - (showBorder ? 4 : 0)) / 2,
            },
          ]}
        >
          <Text style={[styles.firstLetterText, { fontSize: FONT_SIZES[size] }]}>
            {nickname.charAt(0).toUpperCase()}
          </Text>
        </View>
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
