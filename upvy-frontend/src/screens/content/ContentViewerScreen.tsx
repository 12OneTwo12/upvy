/**
 * 콘텐츠 뷰어 화면
 *
 * FeedScreen과 동일하지만 스크롤 비활성화 (단일 콘텐츠만 표시)
 */

import React, { useState, useMemo } from 'react';
import { View, Dimensions, ActivityIndicator, TouchableOpacity, Share, Alert } from 'react-native';
import { useRoute, useNavigation, useIsFocused } from '@react-navigation/native';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { Ionicons } from '@expo/vector-icons';
import { FeedItem } from '@/components/feed';
import { CommentModal } from '@/components/comment';
import { getContent } from '@/api/content.api';
import { getProfileByUserId } from '@/api/auth.api';
import { createLike, deleteLike } from '@/api/like.api';
import { createSave, deleteSave } from '@/api/save.api';
import { shareContent, getShareLink } from '@/api/share.api';
import { followUser, unfollowUser } from '@/api/follow.api';
import type { FeedItem as FeedItemType } from '@/types/feed.types';

const { height: SCREEN_HEIGHT } = Dimensions.get('window');

// 로딩 중일 때 보여줄 스켈레톤 아이템 (FeedScreen과 동일한 패턴)
const loadingFeedItem: FeedItemType = {
  contentId: 'loading',
  contentType: 'VIDEO',
  url: '',
  photoUrls: null,
  thumbnailUrl: '',
  duration: 0,
  width: 1080,
  height: 1920,
  title: '',
  description: '',
  category: 'OTHER',
  tags: [],
  creator: {
    userId: 'loading',
    nickname: '',
    profileImageUrl: null,
  },
  interactions: {
    likeCount: 0,
    commentCount: 0,
    saveCount: 0,
    shareCount: 0,
  },
  subtitles: [],
};

export default function ContentViewerScreen() {
  const { t } = useTranslation('feed');
  const route = useRoute<any>();
  const navigation = useNavigation<any>();
  const isScreenFocused = useIsFocused();
  const { contentId } = route.params;

  const [commentModalVisible, setCommentModalVisible] = useState(false);
  const queryClient = useQueryClient();

  // 콘텐츠 데이터 로드 (인터랙션 정보 포함)
  const { data: content, isLoading: contentLoading } = useQuery({
    queryKey: ['content', contentId],
    queryFn: () => getContent(contentId),
    retry: 3,
    retryDelay: (attemptIndex) => Math.min(1000 * 2 ** attemptIndex, 30000),
  });

  // 크리에이터 프로필 로드 (콘텐츠 로드 후)
  const { data: creatorProfile, isLoading: creatorLoading } = useQuery({
    queryKey: ['profile', content?.creatorId],
    queryFn: () => getProfileByUserId(content!.creatorId),
    enabled: !!content?.creatorId,
    retry: 3,
    retryDelay: (attemptIndex) => Math.min(1000 * 2 ** attemptIndex, 30000),
  });

  const isLoading = contentLoading || creatorLoading;

  // ContentResponse를 FeedItem 타입으로 변환
  const feedItem: FeedItemType | null = content && creatorProfile
    ? {
        contentId: content.id,
        contentType: content.contentType as 'VIDEO' | 'PHOTO',
        url: content.url,
        photoUrls: content.photoUrls,
        thumbnailUrl: content.thumbnailUrl,
        duration: content.duration ?? 0,
        width: content.width,
        height: content.height,
        title: content.title,
        description: content.description ?? '',
        category: content.category as any,
        tags: content.tags,
        creator: {
          userId: content.creatorId,
          nickname: creatorProfile.nickname,
          profileImageUrl: creatorProfile.profileImageUrl ?? null,
          isFollowing: creatorProfile.isFollowing,
        },
        interactions: {
          likeCount: content.interactions?.likeCount ?? 0,
          commentCount: content.interactions?.commentCount ?? 0,
          saveCount: content.interactions?.saveCount ?? 0,
          shareCount: content.interactions?.shareCount ?? 0,
          viewCount: content.interactions?.viewCount ?? 0,
          isLiked: content.interactions?.isLiked ?? false,
          isSaved: content.interactions?.isSaved ?? false,
        },
        subtitles: [],
      }
    : null;

  // 좋아요 mutation (Optimistic update)
  const likeMutation = useMutation({
    mutationFn: async ({ isLiked }: { isLiked: boolean }) => {
      if (isLiked) {
        return await deleteLike(contentId);
      } else {
        return await createLike(contentId);
      }
    },
    onSuccess: (response) => {
      // 콘텐츠의 인터랙션 정보 업데이트
      queryClient.setQueryData(['content', contentId], (oldData: any) => {
        if (!oldData) return oldData;
        return {
          ...oldData,
          interactions: {
            ...oldData.interactions,
            likeCount: response.likeCount,
            isLiked: response.isLiked,
          },
        };
      });
    },
  });

  // 저장 mutation (Optimistic update)
  const saveMutation = useMutation({
    mutationFn: async ({ isSaved }: { isSaved: boolean }) => {
      if (isSaved) {
        return await deleteSave(contentId);
      } else {
        return await createSave(contentId);
      }
    },
    onSuccess: (response) => {
      // 콘텐츠의 인터랙션 정보 업데이트
      queryClient.setQueryData(['content', contentId], (oldData: any) => {
        if (!oldData) return oldData;
        return {
          ...oldData,
          interactions: {
            ...oldData.interactions,
            saveCount: response.saveCount,
            isSaved: response.isSaved,
          },
        };
      });

      // Profile 화면의 저장된 콘텐츠 목록 자동 새로고침
      queryClient.invalidateQueries({ queryKey: ['savedContents'] });
    },
  });

  // 팔로우 mutation
  const followMutation = useMutation({
    mutationFn: async ({ userId, isFollowing }: { userId: string; isFollowing: boolean }) => {
      if (isFollowing) {
        await unfollowUser(userId);
      } else {
        await followUser(userId);
      }
    },
    onSuccess: () => {
      // 프로필 다시 로드
      queryClient.invalidateQueries({ queryKey: ['profile', content?.creatorId] });
    },
  });

  // 공유 mutation (Optimistic update)
  const shareMutation = useMutation({
    mutationFn: async () => {
      return await shareContent(contentId);
    },
    onMutate: async () => {
      await queryClient.cancelQueries({ queryKey: ['content', contentId] });
      const previousData = queryClient.getQueryData(['content', contentId]);

      queryClient.setQueryData(['content', contentId], (oldData: any) => {
        if (!oldData) return oldData;
        return {
          ...oldData,
          interactions: {
            ...oldData.interactions,
            shareCount: (oldData.interactions?.shareCount ?? 0) + 1,
          },
        };
      });

      return { previousData };
    },
    onSuccess: (response) => {
      // 콘텐츠의 인터랙션 정보 업데이트
      queryClient.setQueryData(['content', contentId], (oldData: any) => {
        if (!oldData) return oldData;
        return {
          ...oldData,
          interactions: {
            ...oldData.interactions,
            shareCount: response.shareCount,
          },
        };
      });
    },
    onError: (err, variables, context: any) => {
      if (context?.previousData) {
        queryClient.setQueryData(['content', contentId], context.previousData);
      }
    },
  });

  // 인터랙션 핸들러 (FeedScreen과 동일)
  const handleLike = () => {
    if (feedItem) {
      likeMutation.mutate({ isLiked: feedItem.interactions.isLiked ?? false });
    }
  };

  const handleComment = () => {
    setCommentModalVisible(true);
  };

  const handleCommentModalClose = () => {
    setCommentModalVisible(false);
    // 댓글 목록 및 콘텐츠 인터랙션 정보 다시 로드 (댓글 수 업데이트)
    queryClient.invalidateQueries({ queryKey: ['comments', contentId] });
    queryClient.invalidateQueries({ queryKey: ['content', contentId] });
  };

  const handleSave = () => {
    if (feedItem) {
      saveMutation.mutate({ isSaved: feedItem.interactions.isSaved ?? false });
    }
  };

  const handleShare = async () => {
    try {
      // 1. 공유 링크 가져오기
      const { shareUrl } = await getShareLink(contentId);

      // 2. 네이티브 공유 시트 열기
      const result = await Share.share({
        message: t('share.message', { url: shareUrl }),
        url: shareUrl,
        title: t('share.title'),
      });

      // 3. 공유 성공 시 카운터 증가
      if (result.action === Share.sharedAction) {
        shareMutation.mutate();
      }
    } catch (error: unknown) {
      // 사용자가 공유를 취소한 경우는 에러로 처리하지 않음 (Android)
      // 참고: 이 에러 메시지는 React Native 버전에 따라 변경될 수 있습니다.
      if (error instanceof Error && error.message.includes('User did not share')) {
        return;
      }
      console.error('Share failed:', error);
      Alert.alert(t('share.failed'), t('share.failedMessage'));
    }
  };

  const handleFollow = () => {
    if (feedItem) {
      followMutation.mutate({
        userId: feedItem.creator.userId,
        isFollowing: feedItem.creator.isFollowing ?? false,
      });
    }
  };

  const handleCreatorPress = () => {
    if (feedItem) {
      navigation.navigate('UserProfile', { userId: feedItem.creator.userId });
    }
  };

  const handleBack = () => {
    navigation.goBack();
  };

  // 삭제 성공 핸들러: 프로필로 돌아가며 콘텐츠 목록 새로고침
  const handleDeleteSuccess = async () => {
    // 프로필 콘텐츠 목록 새로고침
    await queryClient.invalidateQueries({ queryKey: ['myContents'] });
    // 이전 화면으로 돌아가기
    navigation.goBack();
  };

  // 표시할 아이템 결정: 로딩 중이면 스켈레톤, 아니면 실제 데이터
  const displayItem = isLoading ? loadingFeedItem : feedItem;

  // 데이터 없음 (로딩 완료 후에도 데이터가 없는 경우)
  if (!isLoading && !feedItem) {
    return (
      <View style={{ flex: 1, backgroundColor: '#000000', justifyContent: 'center', alignItems: 'center' }}>
        <Ionicons name="alert-circle" size={64} color="#666666" />
      </View>
    );
  }

  return (
    <View style={{ flex: 1, backgroundColor: '#000000' }}>
      {/* 뒤로가기 버튼 */}
      <TouchableOpacity
        onPress={handleBack}
        style={{
          position: 'absolute',
          top: 50,
          left: 16,
          zIndex: 100,
          width: 40,
          height: 40,
          borderRadius: 20,
          backgroundColor: 'rgba(0, 0, 0, 0.5)',
          justifyContent: 'center',
          alignItems: 'center',
        }}
      >
        <Ionicons name="arrow-back" size={24} color="#FFFFFF" />
      </TouchableOpacity>

      {/* 콘텐츠 - FeedScreen과 동일 (스크롤 없음) */}
      <View style={{ height: SCREEN_HEIGHT, backgroundColor: '#000000' }}>
        <FeedItem
          item={displayItem!}
          isFocused={!isLoading && isScreenFocused}
          shouldPreload={true}
          hasBeenLoaded={false}
          onVideoLoaded={() => {}}
          onLike={handleLike}
          onComment={handleComment}
          onSave={handleSave}
          onShare={handleShare}
          onFollow={handleFollow}
          onCreatorPress={handleCreatorPress}
          onDeleteSuccess={handleDeleteSuccess}
        />

        {/* 로딩 중일 때 중앙 스피너 추가 (FeedScreen과 동일) */}
        {isLoading && (
          <View
            style={{
              position: 'absolute',
              top: 0,
              left: 0,
              right: 0,
              bottom: 0,
              justifyContent: 'center',
              alignItems: 'center',
              pointerEvents: 'none',
            }}
          >
            <ActivityIndicator size="large" color="#FFFFFF" />
          </View>
        )}
      </View>

      {/* 댓글 모달 */}
      <CommentModal
        visible={commentModalVisible}
        contentId={contentId}
        onClose={handleCommentModalClose}
      />
    </View>
  );
}
