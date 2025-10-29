/**
 * 피드 오버레이 컴포넌트
 *
 * Instagram Reels 스타일의 정보 오버레이
 * - 하단 좌측: 크리에이터 정보, 제목, 설명
 * - 하단 우측: 인터랙션 버튼 (좋아요, 댓글, 저장, 공유)
 */

import React from 'react';
import { View, Text, TouchableOpacity, Image } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
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
    <View className="absolute bottom-0 left-0 right-0 pb-8">
      <View className="flex-row justify-between items-end px-4">
        {/* 좌측: 크리에이터 정보 */}
        <View className="flex-1 mr-4">
          <TouchableOpacity
            onPress={onCreatorPress}
            className="flex-row items-center mb-3"
          >
            {creator.profileImageUrl ? (
              <Image
                source={{ uri: creator.profileImageUrl }}
                className="w-10 h-10 rounded-full border-2 border-white"
              />
            ) : (
              <View className="w-10 h-10 rounded-full bg-gray-700 items-center justify-center border-2 border-white">
                <Ionicons name="person" size={20} color="white" />
              </View>
            )}
            <Text className="text-white font-semibold text-base ml-2">
              {creator.nickname}
            </Text>
          </TouchableOpacity>

          {/* 제목 */}
          <Text className="text-white font-semibold text-base mb-1" numberOfLines={2}>
            {title}
          </Text>

          {/* 설명 */}
          {description && (
            <Text className="text-white/90 text-sm" numberOfLines={3}>
              {description}
            </Text>
          )}
        </View>

        {/* 우측: 인터랙션 버튼 */}
        <View className="items-center space-y-6">
          {/* 좋아요 */}
          <TouchableOpacity onPress={onLike} className="items-center">
            <View className="bg-black/40 rounded-full p-3 mb-1">
              <Ionicons name="heart" size={28} color="white" />
            </View>
            <Text className="text-white text-xs font-medium">
              {formatCount(interactions.likeCount)}
            </Text>
          </TouchableOpacity>

          {/* 댓글 */}
          <TouchableOpacity onPress={onComment} className="items-center">
            <View className="bg-black/40 rounded-full p-3 mb-1">
              <Ionicons name="chatbubble" size={28} color="white" />
            </View>
            <Text className="text-white text-xs font-medium">
              {formatCount(interactions.commentCount)}
            </Text>
          </TouchableOpacity>

          {/* 저장 */}
          <TouchableOpacity onPress={onSave} className="items-center">
            <View className="bg-black/40 rounded-full p-3 mb-1">
              <Ionicons name="bookmark" size={28} color="white" />
            </View>
            <Text className="text-white text-xs font-medium">
              {formatCount(interactions.saveCount)}
            </Text>
          </TouchableOpacity>

          {/* 공유 */}
          <TouchableOpacity onPress={onShare} className="items-center">
            <View className="bg-black/40 rounded-full p-3 mb-1">
              <Ionicons name="paper-plane" size={28} color="white" />
            </View>
            <Text className="text-white text-xs font-medium">
              {formatCount(interactions.shareCount)}
            </Text>
          </TouchableOpacity>
        </View>
      </View>
    </View>
  );
};
