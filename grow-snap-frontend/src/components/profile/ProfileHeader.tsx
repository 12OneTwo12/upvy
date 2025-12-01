import React from 'react';
import { View, Text } from 'react-native';
import { theme } from '@/theme';
import { createStyleSheet } from '@/utils/styles';
import ProfileAvatar from './ProfileAvatar';
import ProfileStats from './ProfileStats';
import { UserProfile } from '@/types/auth.types';

interface ProfileHeaderProps {
  profile: UserProfile;
  isOwnProfile?: boolean;
  contentCount?: number;
  onAvatarPress?: () => void;
  onFollowersPress?: () => void;
  onFollowingPress?: () => void;
  showStats?: boolean;
}

const useStyles = createStyleSheet({
  container: {
    backgroundColor: theme.colors.background.primary,
    paddingHorizontal: theme.spacing[4],
    paddingVertical: theme.spacing[4],
  },
  topSection: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: theme.spacing[4],
  },
  avatar: {
    marginRight: theme.spacing[5],
  },
  statsContainer: {
    flex: 1,
  },
  infoSection: {
    marginTop: theme.spacing[2],
  },
  nickname: {
    fontSize: theme.typography.fontSize.lg,
    fontWeight: theme.typography.fontWeight.bold,
    color: theme.colors.text.primary,
    marginBottom: theme.spacing[2],
  },
  bio: {
    fontSize: theme.typography.fontSize.base,
    fontWeight: theme.typography.fontWeight.normal,
    color: theme.colors.text.primary,
    lineHeight: theme.typography.fontSize.base * 1.5,
  },
});

/**
 * 프로필 헤더 컴포넌트
 * 인스타그램 스타일의 프로필 정보 표시
 */
export default function ProfileHeader({
  profile,
  isOwnProfile = false,
  contentCount = 0,
  onAvatarPress,
  onFollowersPress,
  onFollowingPress,
  showStats = true,
}: ProfileHeaderProps) {
  const styles = useStyles();
  const stats = [
    {
      label: '콘텐츠',
      value: contentCount,
    },
    {
      label: '팔로워',
      value: profile.followerCount,
      onPress: onFollowersPress,
    },
    {
      label: '팔로잉',
      value: profile.followingCount,
      onPress: onFollowingPress,
    },
  ];

  return (
    <View style={styles.container}>
      {/* 프로필 상단: 아바타 + 통계 */}
      <View style={styles.topSection}>
        <ProfileAvatar
          imageUrl={profile.profileImageUrl}
          size="xlarge"
          onPress={onAvatarPress}
          showBorder={!isOwnProfile}
          style={styles.avatar}
        />
        {showStats && (
          <View style={styles.statsContainer}>
            <ProfileStats stats={stats} />
          </View>
        )}
      </View>

      {/* 닉네임 */}
      <View style={styles.infoSection}>
        <Text style={styles.nickname}>{profile.nickname}</Text>

        {/* 자기소개 */}
        {profile.bio && (
          <Text style={styles.bio} numberOfLines={0}>
            {profile.bio}
          </Text>
        )}
      </View>
    </View>
  );
}
