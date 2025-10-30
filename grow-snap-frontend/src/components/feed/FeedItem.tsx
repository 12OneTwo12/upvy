/**
 * 피드 아이템 컴포넌트
 *
 * 비디오 플레이어 + 오버레이를 결합한 완전한 피드 아이템
 */

import React, { useState, useRef } from 'react';
import { View, Dimensions, Animated, PanResponder, Platform } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useBottomTabBarHeight } from '@react-navigation/bottom-tabs';
import { VideoPlayer, VideoPlayerRef } from './VideoPlayer';
import { FeedOverlay } from './FeedOverlay';
import type { FeedItem as FeedItemType } from '@/types/feed.types';

const { height: SCREEN_HEIGHT, width: SCREEN_WIDTH } = Dimensions.get('window');

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
  const tabBarHeight = useBottomTabBarHeight();
  const [isExpanded, setIsExpanded] = useState(false);
  const [progress, setProgress] = useState(0);
  const [duration, setDuration] = useState(0);
  const [isDragging, setIsDragging] = useState(false);
  const progressAnim = useRef(new Animated.Value(0)).current;
  const videoPlayerRef = useRef<VideoPlayerRef>(null);

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
    // duration은 항상 업데이트
    if (dur > 0 && dur !== duration) {
      console.log('Duration updated in FeedItem:', dur);
      setDuration(dur);
    }

    // 드래그 중이 아닐 때만 progress 업데이트
    if (!dragging) {
      setProgress(prog);
      Animated.timing(progressAnim, {
        toValue: prog,
        duration: 100,
        useNativeDriver: false,
      }).start();
    }
  };

  // Seek 함수 - FeedItem의 duration을 VideoPlayer에 전달
  const handleSeek = async (seekProgress: number) => {
    if (videoPlayerRef.current && duration > 0) {
      console.log('handleSeek called - progress:', seekProgress, 'FeedItem duration:', duration);
      await videoPlayerRef.current.seek(seekProgress, duration);
    }
  };

  // 재생바 드래그
  const dragStartDuration = useRef<number>(0);

  const panResponder = useRef(
    PanResponder.create({
      onStartShouldSetPanResponder: () => true,
      onMoveShouldSetPanResponder: () => true,
      onPanResponderGrant: (event) => {
        setIsDragging(true);
        // VideoPlayer에서 직접 duration 가져오기
        const videoDuration = videoPlayerRef.current?.getDuration() || 0;
        dragStartDuration.current = videoDuration;
        console.log('Drag started - duration from VideoPlayer:', dragStartDuration.current);
      },
      onPanResponderMove: (event, gestureState) => {
        // pageX: 화면 전체 기준 X 좌표 사용
        const pageX = event.nativeEvent.pageX;
        const newProgress = Math.max(0, Math.min(1, pageX / SCREEN_WIDTH));
        console.log('Dragging - pageX:', pageX, 'progress:', newProgress);
        setProgress(newProgress);
        progressAnim.setValue(newProgress);
      },
      onPanResponderRelease: async (event, gestureState) => {
        // pageX: 화면 전체 기준 X 좌표 사용
        const pageX = event.nativeEvent.pageX;
        const newProgress = Math.max(0, Math.min(1, pageX / SCREEN_WIDTH));
        const seekDuration = dragStartDuration.current;
        console.log('Drag released - pageX:', pageX, 'progress:', newProgress, 'duration:', seekDuration);

        // 저장된 duration 사용해서 seek
        if (videoPlayerRef.current && seekDuration > 0) {
          await videoPlayerRef.current.seek(newProgress, seekDuration);
        }

        // seek 후 약간의 딜레이를 줘서 position이 안정화되도록 함
        await new Promise(resolve => setTimeout(resolve, 100));

        console.log('Dragging ended, resuming playback');
        setIsDragging(false);
      },
    })
  ).current;

  return (
    <View style={{ height: SCREEN_HEIGHT }} className="relative">
      {/* 비디오 플레이어 */}
      <VideoPlayer
        ref={videoPlayerRef}
        uri={item.url}
        isFocused={isFocused}
        shouldPreload={shouldPreload}
        hasBeenLoaded={hasBeenLoaded}
        isDragging={isDragging}
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
        tabBarHeight={tabBarHeight}
      />

      {/* 비디오 진행률 바 - 탭바 바로 위 */}
      {item.contentType === 'VIDEO' && item.url && (
        <View
          {...panResponder.panHandlers}
          style={{
            position: 'absolute',
            bottom: tabBarHeight - 8.2,
            left: 0,
            right: 0,
            height: 20,
            justifyContent: 'center',
            zIndex: 100,
          }}
        >
          <Animated.View style={{
            height: isDragging ? 5 : 3,
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
          </Animated.View>
        </View>
      )}
    </View>
  );
};
