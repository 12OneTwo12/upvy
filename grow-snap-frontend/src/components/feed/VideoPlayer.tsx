/**
 * 비디오 플레이어 컴포넌트
 *
 * Instagram Reels 스타일의 풀스크린 비디오 플레이어
 * - 탭: 일시정지/재생
 * - 더블탭: 좋아요
 * - 자동재생
 */

import React, { useRef, useState, useEffect } from 'react';
import { View, TouchableWithoutFeedback, Dimensions, Animated, ActivityIndicator } from 'react-native';
import { Video, ResizeMode, AVPlaybackStatus } from 'expo-av';
import { Ionicons } from '@expo/vector-icons';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

const { width: SCREEN_WIDTH, height: SCREEN_HEIGHT } = Dimensions.get('window');
const NAVIGATION_BAR_HEIGHT = 56;
const TOP_TAB_AREA = 50;

interface VideoPlayerProps {
  uri: string;
  isFocused: boolean;
  shouldPreload?: boolean; // Pre-loading을 위한 prop (±2 범위)
  onDoubleTap?: () => void;
  onTap?: () => boolean; // true 반환 시 비디오 탭 무시
}

export const VideoPlayer: React.FC<VideoPlayerProps> = ({
  uri,
  isFocused,
  shouldPreload = false,
  onDoubleTap,
  onTap,
}) => {
  const videoRef = useRef<Video>(null);
  const [isPlaying, setIsPlaying] = useState(false);
  const [isBuffering, setIsBuffering] = useState(true);
  const [showPlayIcon, setShowPlayIcon] = useState<'play' | 'pause' | null>(null);
  const [progress, setProgress] = useState(0);
  const lastTap = useRef<number>(0);
  const heartScale = useRef(new Animated.Value(0)).current;
  const insets = useSafeAreaInsets();

  // 빈 URL인 경우 (스켈레톤 로딩 상태)
  const isLoadingSkeleton = !uri || uri === '';

  // Pre-loading: 현재 포커스이거나 Pre-loading 범위(±2)에 있을 때 Video 컴포넌트 렌더
  const shouldRenderVideo = !isLoadingSkeleton && (isFocused || shouldPreload);

  // 비디오 컨테이너 높이: 상단 safe area + 탭 영역 + 하단 네비게이션 바 + 하단 safe area를 제외한 높이
  const videoContainerHeight = SCREEN_HEIGHT - NAVIGATION_BAR_HEIGHT - insets.bottom;

  // 포커스 상태에 따라 비디오 재생/정지만 제어 (로드는 Video 컴포넌트가 자동 처리)
  // Pre-loading된 비디오는 포커스될 때까지 일시정지 상태 유지
  useEffect(() => {
    const controlPlayback = async () => {
      if (videoRef.current && shouldRenderVideo) {
        try {
          const status = await videoRef.current.getStatusAsync();

          if (status.isLoaded) {
            if (isFocused) {
              // 포커스 시 재생
              if (!status.isPlaying) {
                await videoRef.current.playAsync();
              }
            } else {
              // 포커스 해제 시 일시정지 (언로드 하지 않음 - Pre-loading 유지)
              if (status.isPlaying) {
                await videoRef.current.pauseAsync();
              }
            }
          }
        } catch (error) {
          // 에러 무시 (백그라운드에서 발생할 수 있음)
        }
      }
    };

    controlPlayback();
  }, [isFocused, shouldRenderVideo]);

  // 컴포넌트 언마운트 시 비디오 정리
  // 주의: Pre-loading을 위해 언로드하지 않음 (일시정지만 수행)
  useEffect(() => {
    return () => {
      if (videoRef.current) {
        videoRef.current.pauseAsync().catch(() => {});
      }
    };
  }, []);

  // 비디오 재생 상태 업데이트
  const handlePlaybackStatusUpdate = (status: AVPlaybackStatus) => {
    if (status.isLoaded) {
      setIsPlaying(status.isPlaying);

      // 버퍼링 상태 업데이트 (재생 중이면 항상 버퍼링 해제)
      if (status.isPlaying) {
        setIsBuffering(false);
      } else if (status.isBuffering) {
        setIsBuffering(true);
      } else {
        // 일시정지 상태 (버퍼링 아님)
        setIsBuffering(false);
      }

      // 진행률 계산
      if (status.durationMillis && status.durationMillis > 0) {
        const progressValue = status.positionMillis / status.durationMillis;
        setProgress(progressValue);
      }

      // 비디오 종료 시 progress 초기화 (isLooping이 true이므로 자동 루프됨)
      if (status.didJustFinish) {
        setProgress(0);
      }
    }
  };

  // 싱글탭: 재생/일시정지
  const handleSingleTap = async () => {
    if (!videoRef.current) return;

    if (isPlaying) {
      // 일시정지 -> play 아이콘 표시
      await videoRef.current.pauseAsync();
      setShowPlayIcon('play');
    } else {
      // 재생 -> pause 아이콘 표시
      await videoRef.current.playAsync();
      setShowPlayIcon('pause');
    }

    setTimeout(() => setShowPlayIcon(null), 800);
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

  return (
    <View style={{ height: videoContainerHeight, backgroundColor: '#000000', position: 'relative' }}>
      <TouchableWithoutFeedback onPress={handleTap}>
        <View style={{ height: videoContainerHeight }}>
          {shouldRenderVideo ? (
            <Video
              ref={videoRef}
              source={{ uri }}
              style={{ width: SCREEN_WIDTH, height: videoContainerHeight }}
              resizeMode={ResizeMode.CONTAIN}
              isLooping
              shouldPlay={isFocused}
              isMuted={false}
              onPlaybackStatusUpdate={handlePlaybackStatusUpdate}
              progressUpdateIntervalMillis={100}
              useNativeControls={false}
            />
          ) : (
            <View style={{ width: SCREEN_WIDTH, height: videoContainerHeight, backgroundColor: '#000000' }} />
          )}

          {/* 로딩 인디케이터 - 화면 중앙 */}
          {isBuffering && shouldRenderVideo && (
            <View style={{
              position: 'absolute',
              top: 0,
              left: 0,
              right: 0,
              bottom: 0,
              justifyContent: 'center',
              alignItems: 'center',
              pointerEvents: 'none',
            }}>
              <ActivityIndicator size="large" color="#FFFFFF" />
            </View>
          )}

          {/* 재생/일시정지 아이콘 */}
          {showPlayIcon && (
            <View style={{
              position: 'absolute',
              top: 0,
              left: 0,
              right: 0,
              bottom: 0,
              justifyContent: 'center',
              alignItems: 'center',
            }}>
              <View style={{
                backgroundColor: 'rgba(0, 0, 0, 0.4)',
                borderRadius: 64,
                padding: 32,
              }}>
                <Ionicons
                  name={showPlayIcon === 'play' ? 'play' : 'pause'}
                  size={64}
                  color="white"
                />
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

        </View>
      </TouchableWithoutFeedback>

      {/* 비디오 진행률 바 - 비디오 컨테이너 바로 아래 */}
      {shouldRenderVideo && (
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
