/**
 * 비디오 플레이어 컴포넌트
 *
 * Instagram Reels 스타일의 풀스크린 비디오 플레이어
 * - 탭: 일시정지/재생
 * - 더블탭: 좋아요
 * - 자동재생
 */

import React, { useRef, useState, useEffect } from 'react';
import { View, TouchableWithoutFeedback, Dimensions, Animated } from 'react-native';
import { Video, ResizeMode, AVPlaybackStatus } from 'expo-av';
import { Ionicons } from '@expo/vector-icons';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

const { width: SCREEN_WIDTH, height: SCREEN_HEIGHT } = Dimensions.get('window');
const NAVIGATION_BAR_HEIGHT = 56;
const TOP_TAB_AREA = 50;

interface VideoPlayerProps {
  uri: string;
  isFocused: boolean;
  onDoubleTap?: () => void;
  onTap?: () => boolean; // true 반환 시 비디오 탭 무시
  muted?: boolean;
  onMutedChange?: (muted: boolean) => void;
}

export const VideoPlayer: React.FC<VideoPlayerProps> = ({
  uri,
  isFocused,
  onDoubleTap,
  onTap,
  muted = false,
  onMutedChange,
}) => {
  const videoRef = useRef<Video>(null);
  const [isPlaying, setIsPlaying] = useState(false);
  const [showPlayIcon, setShowPlayIcon] = useState(false);
  const [progress, setProgress] = useState(0);
  const lastTap = useRef<number>(0);
  const heartScale = useRef(new Animated.Value(0)).current;
  const insets = useSafeAreaInsets();

  // 빈 URL인 경우 (로딩 상태)
  const isLoading = !uri || uri === '';

  // 비디오 컨테이너 높이: 상단 safe area + 탭 영역 + 하단 네비게이션 바 + 하단 safe area를 제외한 높이
  const videoContainerHeight = SCREEN_HEIGHT - NAVIGATION_BAR_HEIGHT - insets.bottom;

  // 포커스 상태에 따라 자동재생/정지
  useEffect(() => {
    const loadAndPlay = async () => {
      if (videoRef.current && !isLoading) {
        if (isFocused) {
          try {
            // 비디오 상태 확인
            const status = await videoRef.current.getStatusAsync();

            // 언로드된 상태면 다시 로드
            if (!status.isLoaded) {
              await videoRef.current.loadAsync({ uri }, {}, false);
            }

            await videoRef.current.playAsync();
          } catch (error) {
            console.log('Video load error:', error);
          }
        } else {
          try {
            await videoRef.current.pauseAsync();
          } catch (error) {
            // 이미 언로드된 경우 무시
          }
        }
      }
    };

    loadAndPlay();
  }, [isFocused, isLoading, uri]);

  // 컴포넌트 언마운트 시 비디오 언로드
  useEffect(() => {
    return () => {
      if (videoRef.current) {
        videoRef.current.unloadAsync().catch(() => {});
      }
    };
  }, []);

  // 비디오 재생 상태 업데이트
  const handlePlaybackStatusUpdate = (status: AVPlaybackStatus) => {
    if (status.isLoaded) {
      setIsPlaying(status.isPlaying);

      // 진행률 계산
      if (status.durationMillis && status.durationMillis > 0) {
        const progressValue = status.positionMillis / status.durationMillis;
        setProgress(progressValue);
      }

      // 비디오 종료 시 루프
      if (status.didJustFinish) {
        videoRef.current?.replayAsync();
        setProgress(0);
      }
    }
  };

  // 싱글탭: 재생/일시정지
  const handleSingleTap = async () => {
    if (!videoRef.current) return;

    if (isPlaying) {
      await videoRef.current.pauseAsync();
    } else {
      await videoRef.current.playAsync();
    }

    // 일시정지 아이콘 표시
    setShowPlayIcon(!isPlaying);
    setTimeout(() => setShowPlayIcon(false), 800);
  };

  // 더블탭: 좋아요
  const handleDoubleTap = () => {
    if (!onDoubleTap) return;

    // 하트 애니메이션
    heartScale.setValue(0);
    Animated.spring(heartScale, {
      toValue: 1,
      friction: 3,
      useNativeDriver: true,
    }).start(() => {
      setTimeout(() => {
        Animated.timing(heartScale, {
          toValue: 0,
          duration: 200,
          useNativeDriver: true,
        }).start();
      }, 500);
    });

    onDoubleTap();
  };

  // 탭 이벤트 처리 (싱글/더블 구분)
  const handleTap = () => {
    const now = Date.now();
    const DOUBLE_TAP_DELAY = 300;

    if (now - lastTap.current < DOUBLE_TAP_DELAY) {
      handleDoubleTap();
    } else {
      setTimeout(() => {
        if (Date.now() - lastTap.current >= DOUBLE_TAP_DELAY) {
          // 싱글탭: 더보기 닫기 또는 일시정지/재생
          const handled = onTap?.();
          // 더보기가 닫혔으면 비디오 탭 무시
          if (!handled) {
            handleSingleTap();
          }
        }
      }, DOUBLE_TAP_DELAY);
    }

    lastTap.current = now;
  };

  // 음소거 토글
  const toggleMuted = async () => {
    const newMuted = !muted;
    if (videoRef.current) {
      await videoRef.current.setIsMutedAsync(newMuted);
    }
    onMutedChange?.(newMuted);
  };

  return (
    <View style={{ height: videoContainerHeight, backgroundColor: '#000000', position: 'relative' }}>
      <TouchableWithoutFeedback onPress={handleTap}>
        <View style={{ height: videoContainerHeight }}>
          {!isLoading ? (
            <Video
              ref={videoRef}
              style={{ width: SCREEN_WIDTH, height: videoContainerHeight }}
              resizeMode={ResizeMode.CONTAIN}
              isLooping
              isMuted={muted}
              onPlaybackStatusUpdate={handlePlaybackStatusUpdate}
              progressUpdateIntervalMillis={100}
              useNativeControls={false}
            />
          ) : (
            <View style={{ width: SCREEN_WIDTH, height: videoContainerHeight, backgroundColor: '#000000' }} />
          )}

          {/* 일시정지 아이콘 */}
          {showPlayIcon && !isPlaying && (
            <View className="absolute inset-0 items-center justify-center">
              <View className="bg-black/40 rounded-full p-8">
                <Ionicons name="play" size={64} color="white" />
              </View>
            </View>
          )}

          {/* 더블탭 하트 애니메이션 */}
          <Animated.View
            className="absolute inset-0 items-center justify-center pointer-events-none"
            style={{
              transform: [{ scale: heartScale }],
              opacity: heartScale,
            }}
          >
            <Ionicons name="heart" size={120} color="white" />
          </Animated.View>

          {/* 음소거 버튼 (로딩 상태에서는 숨김) */}
          {!isLoading && (
            <TouchableWithoutFeedback onPress={toggleMuted}>
              <View className="absolute bottom-32 right-4 bg-black/40 rounded-full p-3">
                <Ionicons
                  name={muted ? 'volume-mute' : 'volume-high'}
                  size={24}
                  color="white"
                />
              </View>
            </TouchableWithoutFeedback>
          )}

        </View>
      </TouchableWithoutFeedback>

      {/* 비디오 진행률 바 - 비디오 컨테이너 바로 아래 */}
      {!isLoading && (
        <View style={{
          position: 'absolute',
          bottom: 0,
          left: 0,
          right: 0,
          height: 3,
          backgroundColor: 'rgba(255, 255, 255, 0.3)',
          zIndex: 100,
        }}>
          <View style={{
            height: '100%',
            width: `${progress * 100}%`,
            backgroundColor: '#FFFFFF',
          }} />
        </View>
      )}
    </View>
  );
};
