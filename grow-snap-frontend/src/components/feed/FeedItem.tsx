/**
 * 피드 아이템 컴포넌트
 *
 * 비디오 플레이어 + 오버레이를 결합한 완전한 피드 아이템
 */

import React, { useState, useRef } from 'react';
import { View, Dimensions, Animated, PanResponder } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { VideoPlayer } from './VideoPlayer';
import { FeedOverlay } from './FeedOverlay';
import type { FeedItem as FeedItemType } from '@/types/feed.types';

const { height: SCREEN_HEIGHT, width: SCREEN_WIDTH } = Dimensions.get('window');
const NAVIGATION_BAR_HEIGHT = 56;

interface FeedItemProps {
  item: FeedItemType;
  isFocused: boolean;
  shouldPreload?: boolean;
  hasBeenLoaded?: boolean; // 외부에서 관리되는 로드 상태
  onVideoLoaded?: () => void; // 로드 완료 콜백
  onLike?: () => void;
  onComment?: () => void;
  onSave?: () => void;
  onShare?: () => void;
  onFollow?: () => void;
  onCreatorPress?: () => void;
}

export const FeedItem: React.FC<FeedItemProps> = ({
  item,
  isFocused,
  shouldPreload = false,
  hasBeenLoaded = false,
  onVideoLoaded,
  onLike,
  onComment,
  onSave,
  onShare,
  onFollow,
  onCreatorPress,
}) => {
  const [isExpanded, setIsExpanded] = useState(false);
  const [progress, setProgress] = useState(0);
  const [duration, setDuration] = useState(0);
  const [isDragging, setIsDragging] = useState(false);
  const progressAnim = useRef(new Animated.Value(0)).current;
  const insets = useSafeAreaInsets();

  // 비디오만 표시 (사진은 나중에 구현)
  if (item.contentType !== 'VIDEO') {
    return null;
  }

  // 영상 탭 시 더보기 닫기
  const handleVideoTap = () => {
    if (isExpanded) {
      setIsExpanded(false);
      return true;
    }
    return false;
  };

  // 진행률 업데이트 받기
  const handleProgressUpdate = (prog: number, dur: number, dragging: boolean) => {
    setProgress(prog);
    setDuration(dur);
    if (!dragging) {
      Animated.timing(progressAnim, {
        toValue: prog,
        duration: 100,
        useNativeDriver: false,
      }).start();
    }
  };

  // Seek 함수
  const handleSeek = async (seekProgress: number) => {
    if (duration > 0) {
      (window as any).videoSeek?.(seekProgress);
    }
  };

  // 재생바 드래그
  const panResponder = useRef(
    PanResponder.create({
      onStartShouldSetPanResponder: () => true,
      onMoveShouldSetPanResponder: () => true,
      onPanResponderGrant: () => {
        setIsDragging(true);
      },
      onPanResponderMove: (_, gestureState) => {
        const newProgress = Math.max(0, Math.min(1, gestureState.moveX / SCREEN_WIDTH));
        setProgress(newProgress);
        progressAnim.setValue(newProgress);
      },
      onPanResponderRelease: (_, gestureState) => {
        const newProgress = Math.max(0, Math.min(1, gestureState.moveX / SCREEN_WIDTH));
        handleSeek(newProgress);
        setIsDragging(false);
      },
    })
  ).current;

  const bottomPosition = NAVIGATION_BAR_HEIGHT + insets.bottom;

  return (
    <View style={{ height: SCREEN_HEIGHT }} className="relative">
      {/* 비디오 플레이어 */}
      <VideoPlayer
        uri={item.url}
        isFocused={isFocused}
        shouldPreload={shouldPreload}
        hasBeenLoaded={hasBeenLoaded}
        onVideoLoaded={onVideoLoaded}
        onDoubleTap={onLike}
        onTap={handleVideoTap}
        onProgressUpdate={handleProgressUpdate}
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
        onFollow={onFollow}
        onCreatorPress={onCreatorPress}
        isExpanded={isExpanded}
        setIsExpanded={setIsExpanded}
      />

      {/* 비디오 진행률 바 - 네비게이션 바 바로 위 */}
      {item.contentType === 'VIDEO' && item.url && (
        <View
          {...panResponder.panHandlers}
          style={{
            position: 'absolute',
            bottom: bottomPosition,
            left: 0,
            right: 0,
            height: 20,
            justifyContent: 'center',
            zIndex: 100,
          }}
        >
          <View style={{
            height: 3,
            backgroundColor: 'rgba(255, 255, 255, 0.3)',
          }}>
            <Animated.View
              style={{
                height: '100%',
                width: progressAnim.interpolate({
                  inputRange: [0, 1],
                  outputRange: ['0%', '100%'],
                }),
                backgroundColor: '#FFFFFF',
              }}
            />
          </View>
        </View>
      )}
    </View>
  );
};
