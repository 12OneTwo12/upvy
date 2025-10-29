/**
 * 피드 오버레이 컴포넌트
 *
 * Instagram Reels 스타일의 정보 오버레이
 * - 하단 좌측: 크리에이터 정보, 제목, 설명
 * - 하단 우측: 인터랙션 버튼 (좋아요, 댓글, 저장, 공유)
 */

import React, { useState, useRef, useEffect } from 'react';
import { View, Text, TouchableOpacity, Image, StyleSheet, ScrollView, Dimensions, Animated } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { LinearGradient } from 'expo-linear-gradient';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import type { CreatorInfo, InteractionInfo } from '@/types/feed.types';

const { height: SCREEN_HEIGHT } = Dimensions.get('window');

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

// 로딩 상태인지 확인
const isLoadingState = (creator: CreatorInfo) => {
  return creator.userId === 'loading';
};

// 내비게이션 바 높이 (일반적으로 48-56px + safe area)
const NAVIGATION_BAR_HEIGHT = 56;

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
  const insets = useSafeAreaInsets();
  const isLoading = isLoadingState(creator);
  const [isExpanded, setIsExpanded] = useState(false);
  const slideAnim = useRef(new Animated.Value(0)).current;

  // 하단 패딩 = 내비게이션 바 높이 + 하단 안전 영역 + 여유 공간
  const bottomPadding = NAVIGATION_BAR_HEIGHT + insets.bottom + 16;

  // 확장/축소 애니메이션 - 위로 적당히 올라가도록 (화면의 20% 정도)
  useEffect(() => {
    Animated.spring(slideAnim, {
      toValue: isExpanded ? -(SCREEN_HEIGHT * 0.15) : 0,
      useNativeDriver: true,
      damping: 20,
      stiffness: 90,
    }).start();
  }, [isExpanded, slideAnim]);

  // 설명이 길면 더보기 표시 (60자 이상)
  const shouldShowMore = (description || title).length > 60;
  const displayText = shouldShowMore
    ? `${(description || title).slice(0, 60)}...`
    : (description || title);

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

      {/* 하단 콘텐츠 - Animated */}
      <Animated.View
        style={[
          styles.container,
          {
            paddingBottom: bottomPadding,
            transform: [{ translateY: slideAnim }],
          }
        ]}
      >
        <View style={styles.content}>
          {/* 좌측: 크리에이터 정보 + 콘텐츠 정보 */}
          <View style={styles.leftSection}>
            {/* 크리에이터 프로필 */}
            {isLoading ? (
              // 스켈레톤 UI
              <View style={styles.creatorContainer}>
                <View style={[styles.profilePlaceholder, styles.skeleton]} />
                <View style={[styles.skeletonText, { width: 100, marginLeft: 8 }]} />
              </View>
            ) : (
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
            )}

            {/* 콘텐츠 설명 */}
            <TouchableOpacity
              style={styles.descriptionContainer}
              onPress={() => shouldShowMore && setIsExpanded(true)}
              disabled={!shouldShowMore || isLoading}
            >
              {isLoading ? (
                // 스켈레톤 UI
                <>
                  <View style={[styles.skeletonText, { width: '90%', marginBottom: 6 }]} />
                  <View style={[styles.skeletonText, { width: '70%' }]} />
                </>
              ) : (
                <>
                  <Text style={styles.description} numberOfLines={2}>
                    {displayText}
                  </Text>
                  {shouldShowMore && (
                    <Text style={styles.moreText}>더보기</Text>
                  )}
                </>
              )}
            </TouchableOpacity>
          </View>

          {/* 우측: 인터랙션 버튼 */}
          <View style={[styles.rightSection, { bottom: bottomPadding }]}>
            {/* 좋아요 */}
            <TouchableOpacity onPress={onLike} style={styles.actionButton}>
              <Ionicons name="heart-outline" size={32} color="#FFFFFF" />
              <Text style={[styles.actionCount, isLoading && { opacity: 0 }]}>
                {isLoading ? '0' : formatCount(interactions.likeCount)}
              </Text>
            </TouchableOpacity>

            {/* 댓글 */}
            <TouchableOpacity onPress={onComment} style={styles.actionButton}>
              <Ionicons name="chatbubble-outline" size={30} color="#FFFFFF" />
              <Text style={[styles.actionCount, isLoading && { opacity: 0 }]}>
                {isLoading ? '0' : formatCount(interactions.commentCount)}
              </Text>
            </TouchableOpacity>

            {/* 저장 */}
            <TouchableOpacity onPress={onSave} style={styles.actionButton}>
              <Ionicons name="bookmark-outline" size={30} color="#FFFFFF" />
              <Text style={[styles.actionCount, isLoading && { opacity: 0 }]}>
                {isLoading ? '0' : formatCount(interactions.saveCount)}
              </Text>
            </TouchableOpacity>

            {/* 공유 */}
            <TouchableOpacity onPress={onShare} style={styles.actionButton}>
              <Ionicons name="paper-plane-outline" size={30} color="#FFFFFF" />
              <Text style={[styles.actionCount, isLoading && { opacity: 0 }]}>
                {isLoading ? '0' : formatCount(interactions.shareCount)}
              </Text>
            </TouchableOpacity>

            {/* 더보기 (점 3개) */}
            <TouchableOpacity style={styles.actionButton}>
              <Ionicons name="ellipsis-vertical" size={24} color="#FFFFFF" />
            </TouchableOpacity>
          </View>
        </View>

        {/* 확장 시 전체 설명 영역 */}
        {isExpanded && (
          <TouchableOpacity
            style={styles.expandedDescriptionContainer}
            activeOpacity={1}
            onPress={() => setIsExpanded(false)}
          >
            <ScrollView style={styles.expandedScrollView}>
              <Text style={styles.expandedDescription}>
                {description || title}
              </Text>
            </ScrollView>
          </TouchableOpacity>
        )}
      </Animated.View>
    </>
  );
};

const styles = StyleSheet.create({
  gradient: {
    position: 'absolute',
    bottom: 0,
    left: 0,
    right: 0,
    height: 250,
  },
  container: {
    position: 'absolute',
    bottom: 0,
    left: 0,
    right: 0,
    paddingHorizontal: 12,
    // paddingBottom은 동적으로 설정됨
  },
  content: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-end',
  },
  leftSection: {
    flex: 1,
    paddingRight: 60,
  },
  creatorContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 10,
  },
  profileImage: {
    width: 38,
    height: 38,
    borderRadius: 19,
    borderWidth: 1.5,
    borderColor: '#FFFFFF',
  },
  profilePlaceholder: {
    width: 38,
    height: 38,
    borderRadius: 19,
    backgroundColor: '#444444',
    borderWidth: 1.5,
    borderColor: '#FFFFFF',
    justifyContent: 'center',
    alignItems: 'center',
  },
  skeleton: {
    backgroundColor: 'rgba(255, 255, 255, 0.15)',
    borderWidth: 0,
  },
  skeletonText: {
    height: 14,
    backgroundColor: 'rgba(255, 255, 255, 0.15)',
    borderRadius: 4,
  },
  creatorName: {
    fontSize: 14,
    fontWeight: '700',
    color: '#FFFFFF',
    marginLeft: 8,
  },
  followButton: {
    marginLeft: 10,
    paddingHorizontal: 12,
    paddingVertical: 4,
    borderRadius: 4,
    borderWidth: 1,
    borderColor: '#FFFFFF',
  },
  followButtonText: {
    fontSize: 13,
    fontWeight: '700',
    color: '#FFFFFF',
  },
  descriptionContainer: {
    marginBottom: 0,
  },
  description: {
    fontSize: 13,
    color: '#FFFFFF',
    lineHeight: 18,
  },
  rightSection: {
    position: 'absolute',
    right: 12,
    // bottom은 동적으로 설정됨
    alignItems: 'center',
    gap: 20,
  },
  actionButton: {
    alignItems: 'center',
    justifyContent: 'center',
    width: 48,
  },
  actionCount: {
    fontSize: 11,
    fontWeight: '600',
    color: '#FFFFFF',
    marginTop: 2,
    textAlign: 'center',
  },
  moreText: {
    fontSize: 13,
    color: '#888888',
    marginTop: 4,
  },
  modalOverlay: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    backgroundColor: 'rgba(0, 0, 0, 0.5)',
  },
  modalContent: {
    position: 'absolute',
    bottom: 0,
    left: 0,
    right: 0,
    backgroundColor: '#1C1C1E',
    borderTopLeftRadius: 16,
    borderTopRightRadius: 16,
    maxHeight: SCREEN_HEIGHT * 0.7,
    paddingHorizontal: 16,
    paddingTop: 8,
    paddingBottom: 24,
  },
  modalHandle: {
    width: 40,
    height: 4,
    backgroundColor: '#48484A',
    borderRadius: 2,
    alignSelf: 'center',
    marginBottom: 16,
  },
  modalHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 16,
  },
  modalProfileImage: {
    width: 32,
    height: 32,
    borderRadius: 16,
  },
  modalProfilePlaceholder: {
    width: 32,
    height: 32,
    borderRadius: 16,
    backgroundColor: '#444444',
    justifyContent: 'center',
    alignItems: 'center',
  },
  modalCreatorName: {
    fontSize: 15,
    fontWeight: '700',
    color: '#FFFFFF',
    marginLeft: 10,
  },
  modalScrollView: {
    maxHeight: SCREEN_HEIGHT * 0.5,
  },
  modalDescription: {
    fontSize: 14,
    color: '#FFFFFF',
    lineHeight: 22,
  },
  expandedDescriptionContainer: {
    paddingTop: 8,
    paddingHorizontal: 12,
    maxHeight: SCREEN_HEIGHT * 0.25,
  },
  expandedScrollView: {
    flex: 1,
  },
  expandedDescription: {
    fontSize: 14,
    color: '#FFFFFF',
    lineHeight: 20,
    paddingBottom: 16,
  },
});
