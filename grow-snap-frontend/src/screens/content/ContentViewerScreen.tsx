/**
 * 콘텐츠 뷰어 화면
 *
 * FeedScreen과 동일하지만 스크롤 비활성화 (단일 콘텐츠만 표시)
 */

import React, { useState } from 'react';
import { View, Dimensions, StatusBar, ActivityIndicator, TouchableOpacity } from 'react-native';
import { useRoute, useNavigation, RouteProp } from '@react-navigation/native';
import { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Ionicons } from '@expo/vector-icons';
import { FeedItem } from '@/components/feed';
import { CommentModal } from '@/components/comment';
import { getContent } from '@/api/content.api';
import { getProfileByUserId } from '@/api/auth.api';
import { createLike, deleteLike, getLikeStatus, getLikeCount } from '@/api/like.api';
import { createSave, deleteSave, getSaveStatus } from '@/api/save.api';
import { shareContent } from '@/api/share.api';
import { followUser, unfollowUser } from '@/api/follow.api';
import { getComments } from '@/api/comment.api';
import { RootStackParamList } from '@/types/navigation.types';
import type { FeedItem as FeedItemType } from '@/types/feed.types';

const { height: SCREEN_HEIGHT } = Dimensions.get('window');

type ContentViewerRouteProp = RouteProp<RootStackParamList, 'ContentViewer'>;
type ContentViewerNavigationProp = NativeStackNavigationProp<RootStackParamList, 'ContentViewer'>;

export default function ContentViewerScreen() {
  const route = useRoute<ContentViewerRouteProp>();
  const navigation = useNavigation<ContentViewerNavigationProp>();
  const { contentId } = route.params;

  const [commentModalVisible, setCommentModalVisible] = useState(false);
  const queryClient = useQueryClient();

  // 콘텐츠 데이터 로드
  const { data: content, isLoading: contentLoading } = useQuery({
    queryKey: ['content', contentId],
    queryFn: () => getContent(contentId),
  });

  // 크리에이터 프로필 로드 (콘텐츠 로드 후)
  const { data: creatorProfile, isLoading: creatorLoading } = useQuery({
    queryKey: ['profile', content?.creatorId],
    queryFn: () => getProfileByUserId(content!.creatorId),
    enabled: !!content?.creatorId,
  });

  // Interaction 정보 병렬 로드
  const { data: likeStatus } = useQuery({
    queryKey: ['likeStatus', contentId],
    queryFn: () => getLikeStatus(contentId),
    enabled: !!content,
  });

  const { data: likeCount } = useQuery({
    queryKey: ['likeCount', contentId],
    queryFn: () => getLikeCount(contentId),
    enabled: !!content,
  });

  const { data: saveStatus } = useQuery({
    queryKey: ['saveStatus', contentId],
    queryFn: () => getSaveStatus(contentId),
    enabled: !!content,
  });

  // NOTE: 백엔드에 전체 댓글 수 조회 API가 없어서 commentCount는 0으로 설정
  // getComments는 첫 페이지(20개)만 가져오므로 정확한 전체 개수를 알 수 없음

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
          likeCount: likeCount?.likeCount ?? 0,
          commentCount: 0, // 백엔드에 전체 댓글 수 조회 API 없음
          saveCount: 0, // 백엔드에서 제공하지 않음 (SaveResponse는 저장/취소 시에만)
          shareCount: 0, // 백엔드에서 제공하지 않음
          isLiked: likeStatus?.isLiked,
          isSaved: saveStatus?.isSaved,
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
      // 백엔드 응답으로 정확한 값 업데이트
      queryClient.setQueryData(['likeStatus', contentId], {
        contentId: response.contentId,
        isLiked: response.isLiked,
      });
      queryClient.setQueryData(['likeCount', contentId], {
        contentId: response.contentId,
        likeCount: response.likeCount,
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
      // 백엔드 응답으로 정확한 값 업데이트
      queryClient.setQueryData(['saveStatus', contentId], {
        contentId: response.contentId,
        isSaved: response.isSaved,
      });
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
    // 댓글 목록 다시 로드 (댓글 수 업데이트)
    queryClient.invalidateQueries({ queryKey: ['comments', contentId] });
  };

  const handleSave = () => {
    if (feedItem) {
      saveMutation.mutate({ isSaved: feedItem.interactions.isSaved ?? false });
    }
  };

  const handleShare = async () => {
    try {
      await shareContent(contentId);
    } catch (error) {
      console.error('Share failed:', error);
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

  // 로딩 중
  if (isLoading) {
    return (
      <View style={{ flex: 1, backgroundColor: '#000000', justifyContent: 'center', alignItems: 'center' }}>
        <StatusBar barStyle="light-content" backgroundColor="#000000" translucent />
        <ActivityIndicator size="large" color="#FFFFFF" />
      </View>
    );
  }

  // 데이터 없음
  if (!feedItem) {
    return (
      <View style={{ flex: 1, backgroundColor: '#000000', justifyContent: 'center', alignItems: 'center' }}>
        <StatusBar barStyle="light-content" backgroundColor="#000000" translucent />
        <Ionicons name="alert-circle" size={64} color="#666666" />
      </View>
    );
  }

  return (
    <View style={{ flex: 1, backgroundColor: '#000000' }}>
      <StatusBar barStyle="light-content" backgroundColor="#000000" translucent />

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
          item={feedItem}
          isFocused={true}
          shouldPreload={true}
          hasBeenLoaded={false}
          onVideoLoaded={() => {}}
          onLike={handleLike}
          onComment={handleComment}
          onSave={handleSave}
          onShare={handleShare}
          onFollow={handleFollow}
          onCreatorPress={handleCreatorPress}
        />
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
