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

const { width: SCREEN_WIDTH, height: SCREEN_HEIGHT } = Dimensions.get('window');

interface VideoPlayerProps {
  uri: string;
  isFocused: boolean;
  onDoubleTap?: () => void;
  muted?: boolean;
  onMutedChange?: (muted: boolean) => void;
}

export const VideoPlayer: React.FC<VideoPlayerProps> = ({
  uri,
  isFocused,
  onDoubleTap,
  muted = false,
  onMutedChange,
}) => {
  const videoRef = useRef<Video>(null);
  const [isPlaying, setIsPlaying] = useState(false);
  const [showPlayIcon, setShowPlayIcon] = useState(false);
  const lastTap = useRef<number>(0);
  const heartScale = useRef(new Animated.Value(0)).current;

  // 빈 URL인 경우 (로딩 상태)
  const isLoading = !uri || uri === '';

  // 포커스 상태에 따라 자동재생/정지
  useEffect(() => {
    if (videoRef.current && !isLoading) {
      if (isFocused) {
        videoRef.current.playAsync();
      } else {
        videoRef.current.pauseAsync();
      }
    }
  }, [isFocused, isLoading]);

  // 비디오 재생 상태 업데이트
  const handlePlaybackStatusUpdate = (status: AVPlaybackStatus) => {
    if (status.isLoaded) {
      setIsPlaying(status.isPlaying);

      // 비디오 종료 시 루프
      if (status.didJustFinish) {
        videoRef.current?.replayAsync();
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
          handleSingleTap();
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
    <View className="flex-1 bg-black">
      <TouchableWithoutFeedback onPress={handleTap}>
        <View className="flex-1">
          {!isLoading ? (
            <Video
              ref={videoRef}
              source={{ uri }}
              style={{ width: SCREEN_WIDTH, height: SCREEN_HEIGHT }}
              resizeMode={ResizeMode.CONTAIN}
              shouldPlay={isFocused}
              isLooping
              isMuted={muted}
              onPlaybackStatusUpdate={handlePlaybackStatusUpdate}
            />
          ) : (
            <View style={{ width: SCREEN_WIDTH, height: SCREEN_HEIGHT, backgroundColor: '#000000' }} />
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
    </View>
  );
};
