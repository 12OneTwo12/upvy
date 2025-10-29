/**
 * 피드 아이템 컴포넌트
 *
 * 비디오 플레이어 + 오버레이를 결합한 완전한 피드 아이템
 */

import React, { useState } from 'react';
import { View, Dimensions } from 'react-native';
import { VideoPlayer } from './VideoPlayer';
import { FeedOverlay } from './FeedOverlay';
import type { FeedItem as FeedItemType } from '@/types/feed.types';

const { height: SCREEN_HEIGHT } = Dimensions.get('window');

interface FeedItemProps {
  item: FeedItemType;
  isFocused: boolean;
  onLike?: () => void;
  onComment?: () => void;
  onSave?: () => void;
  onShare?: () => void;
  onCreatorPress?: () => void;
}

export const FeedItem: React.FC<FeedItemProps> = ({
  item,
  isFocused,
  onLike,
  onComment,
  onSave,
  onShare,
  onCreatorPress,
}) => {
  const [muted, setMuted] = useState(false);

  // 비디오만 표시 (사진은 나중에 구현)
  if (item.contentType !== 'VIDEO') {
    return null;
  }

  return (
    <View style={{ height: SCREEN_HEIGHT }} className="relative">
      {/* 비디오 플레이어 */}
      <VideoPlayer
        uri={item.url}
        isFocused={isFocused}
        onDoubleTap={onLike}
        muted={muted}
        onMutedChange={setMuted}
      />

      {/* 정보 오버레이 */}
      <FeedOverlay
        creator={item.creator}
        title={item.title}
        description={item.description}
        interactions={item.interactions}
        onLike={onLike}
        onComment={onComment}
        onSave={onSave}
        onShare={onShare}
        onCreatorPress={onCreatorPress}
      />
    </View>
  );
};
