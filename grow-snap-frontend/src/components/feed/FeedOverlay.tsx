/**
 * 피드 오버레이 컴포넌트
 *
 * Instagram Reels 스타일의 정보 오버레이
 * - 하단 좌측: 크리에이터 정보, 제목, 설명
 * - 하단 우측: 인터랙션 버튼 (좋아요, 댓글, 저장, 공유)
 */

import React from 'react';
import { View, Text, TouchableOpacity, Image, StyleSheet } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { LinearGradient } from 'expo-linear-gradient';
import type { CreatorInfo, InteractionInfo } from '@/types/feed.types';

interface FeedOverlayProps {
  creator: CreatorInfo;
  title: string;
  description: string | null;
  interactions: InteractionInfo;
  onLike?: () => void;
  onComment?: () => void;
  onSave?: () => void;
  onShare?: () => void;
  onCreatorPress?: () => void;
}

export const FeedOverlay: React.FC<FeedOverlayProps> = ({
  creator,
  title,
  description,
  interactions,
  onLike,
  onComment,
  onSave,
  onShare,
  onCreatorPress,
}) => {
  const formatCount = (count: number): string => {
    if (count >= 1000000) {
      return `${(count / 1000000).toFixed(1)}M`;
    }
    if (count >= 1000) {
      return `${(count / 1000).toFixed(1)}K`;
    }
    return count.toString();
  };

  return (
    <>
      {/* 하단 그라디언트 오버레이 */}
      <LinearGradient
        colors={['transparent', 'rgba(0,0,0,0.6)']}
        style={styles.gradient}
        pointerEvents="none"
      />

      {/* 하단 콘텐츠 */}
      <View style={styles.container}>
        <View style={styles.content}>
          {/* 좌측: 크리에이터 정보 + 콘텐츠 정보 */}
          <View style={styles.leftSection}>
            {/* 크리에이터 프로필 */}
            <TouchableOpacity
              onPress={onCreatorPress}
              style={styles.creatorContainer}
            >
              {creator.profileImageUrl ? (
                <Image
                  source={{ uri: creator.profileImageUrl }}
                  style={styles.profileImage}
                />
              ) : (
                <View style={styles.profilePlaceholder}>
                  <Ionicons name="person" size={20} color="#FFFFFF" />
                </View>
              )}
              <Text style={styles.creatorName}>{creator.nickname}</Text>
              <TouchableOpacity style={styles.followButton}>
                <Text style={styles.followButtonText}>팔로우</Text>
              </TouchableOpacity>
            </TouchableOpacity>

            {/* 콘텐츠 설명 */}
            <View style={styles.descriptionContainer}>
              <Text style={styles.description} numberOfLines={2}>
                {description || title}
              </Text>
            </View>
          </View>

          {/* 우측: 인터랙션 버튼 */}
          <View style={styles.rightSection}>
            {/* 좋아요 */}
            <TouchableOpacity onPress={onLike} style={styles.actionButton}>
              <Ionicons name="heart-outline" size={32} color="#FFFFFF" />
              <Text style={styles.actionCount}>
                {formatCount(interactions.likeCount)}
              </Text>
            </TouchableOpacity>

            {/* 댓글 */}
            <TouchableOpacity onPress={onComment} style={styles.actionButton}>
              <Ionicons name="chatbubble-outline" size={30} color="#FFFFFF" />
              <Text style={styles.actionCount}>
                {formatCount(interactions.commentCount)}
              </Text>
            </TouchableOpacity>

            {/* 저장 */}
            <TouchableOpacity onPress={onSave} style={styles.actionButton}>
              <Ionicons name="bookmark-outline" size={30} color="#FFFFFF" />
              <Text style={styles.actionCount}>
                {formatCount(interactions.saveCount)}
              </Text>
            </TouchableOpacity>

            {/* 공유 */}
            <TouchableOpacity onPress={onShare} style={styles.actionButton}>
              <Ionicons name="paper-plane-outline" size={30} color="#FFFFFF" />
              <Text style={styles.actionCount}>
                {formatCount(interactions.shareCount)}
              </Text>
            </TouchableOpacity>

            {/* 더보기 (점 3개) */}
            <TouchableOpacity style={styles.actionButton}>
              <Ionicons name="ellipsis-vertical" size={24} color="#FFFFFF" />
            </TouchableOpacity>
          </View>
        </View>
      </View>
    </>
  );
};

const styles = StyleSheet.create({
  gradient: {
    position: 'absolute',
    bottom: 0,
    left: 0,
    right: 0,
    height: 300,
  },
  container: {
    position: 'absolute',
    bottom: 0,
    left: 0,
    right: 0,
    paddingBottom: 20,
  },
  content: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-end',
    paddingHorizontal: 12,
  },
  leftSection: {
    flex: 1,
    marginRight: 12,
  },
  creatorContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 12,
  },
  profileImage: {
    width: 40,
    height: 40,
    borderRadius: 20,
    borderWidth: 2,
    borderColor: '#FFFFFF',
  },
  profilePlaceholder: {
    width: 40,
    height: 40,
    borderRadius: 20,
    backgroundColor: '#333333',
    borderWidth: 2,
    borderColor: '#FFFFFF',
    justifyContent: 'center',
    alignItems: 'center',
  },
  creatorName: {
    fontSize: 15,
    fontWeight: '700',
    color: '#FFFFFF',
    marginLeft: 10,
  },
  followButton: {
    marginLeft: 12,
    paddingHorizontal: 16,
    paddingVertical: 6,
    borderRadius: 6,
    borderWidth: 1,
    borderColor: '#FFFFFF',
  },
  followButtonText: {
    fontSize: 14,
    fontWeight: '700',
    color: '#FFFFFF',
  },
  descriptionContainer: {
    marginBottom: 8,
  },
  description: {
    fontSize: 14,
    color: '#FFFFFF',
    lineHeight: 20,
  },
  rightSection: {
    alignItems: 'center',
    gap: 24,
  },
  actionButton: {
    alignItems: 'center',
    justifyContent: 'center',
  },
  actionCount: {
    fontSize: 12,
    fontWeight: '600',
    color: '#FFFFFF',
    marginTop: 4,
  },
});
