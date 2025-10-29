/**
 * 비디오 플레이어 컴포넌트
 *
 * Instagram Reels 스타일의 풀스크린 비디오 플레이어
 * - 탭: 일시정지/재생
 * - 더블탭: 좋아요
 * - 자동재생
 */

import React, { useRef, useState, useEffect, useMemo } from 'react';
import { View, TouchableWithoutFeedback, Dimensions, Animated, ActivityIndicator, PanResponder } from 'react-native';
import { Video, ResizeMode, AVPlaybackStatus } from 'expo-av';
import { Ionicons } from '@expo/vector-icons';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

const { width: SCREEN_WIDTH, height: SCREEN_HEIGHT } = Dimensions.get('window');
const NAVIGATION_BAR_HEIGHT = 56;
const TOP_TAB_AREA = 50;

interface VideoPlayerProps {
  uri: string;
  isFocused: boolean;
  shouldPreload?: boolean;
  hasBeenLoaded?: boolean;
  onVideoLoaded?: () => void;
  onDoubleTap?: () => void;
  onTap?: () => boolean;
  onProgressUpdate?: (progress: number, duration: number, isDragging: boolean) => void;
}

export const VideoPlayer: React.FC<VideoPlayerProps> = ({
  uri,
  isFocused,
  shouldPreload = false,
  hasBeenLoaded = false,
  onVideoLoaded,
  onDoubleTap,
  onTap,
  onProgressUpdate,
}) => {
  const videoRef = useRef<Video>(null);
  const [isPlaying, setIsPlaying] = useState(false);
  const [isBuffering, setIsBuffering] = useState(true);
  const [showPlayIcon, setShowPlayIcon] = useState<'play' | 'pause' | null>(null);
  const [progress, setProgress] = useState(0);
  const [duration, setDuration] = useState(0);
  const [isDragging, setIsDragging] = useState(false);
  const lastTap = useRef<number>(0);
  const heartScale = useRef(new Animated.Value(0)).current;
  const progressAnim = useRef(new Animated.Value(0)).current;
  const insets = useSafeAreaInsets();

  // 빈 URL인 경우 (스켈레톤 로딩 상태)
  const isLoadingSkeleton = !uri || uri === '';

  // Pre-loading 로직: 외부에서 관리되는 hasBeenLoaded 사용
  const shouldRenderVideo = !isLoadingSkeleton && (isFocused || shouldPreload || hasBeenLoaded);

  // 비디오 컨테이너 높이: 상단 safe area + 탭 영역 + 하단 네비게이션 바 + 하단 safe area를 제외한 높이
  const videoContainerHeight = SCREEN_HEIGHT - NAVIGATION_BAR_HEIGHT - insets.bottom;

  // Video source 메모이제이션 (재생성 방지)
  const videoSource = useMemo(() => ({ uri }), [uri]);


  // 포커스 상태에 따라 비디오 재생/정지만 제어
  useEffect(() => {
    const controlPlayback = async () => {
      if (videoRef.current && !isLoadingSkeleton) {
        try {
          const status = await videoRef.current.getStatusAsync();

          if (status.isLoaded) {
            // 로드 완료 시 외부 콜백 호출 (한 번만)
            if (!hasBeenLoaded && onVideoLoaded) {
              onVideoLoaded();
            }

            if (isFocused && !isDragging) {
              // 포커스 시 재생
              if (!status.isPlaying) {
                await videoRef.current.playAsync();
              }
            } else {
              // 포커스 해제 시 일시정지
              if (status.isPlaying) {
                await videoRef.current.pauseAsync();
              }
            }
          }
        } catch (error) {
          // 에러 무시
        }
      }
    };

    controlPlayback();
  }, [isFocused, isLoadingSkeleton, isDragging, hasBeenLoaded, onVideoLoaded]);

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

      // Duration 저장
      if (status.durationMillis) {
        setDuration(status.durationMillis);
      }

      // 버퍼링 상태 업데이트
      if (status.isPlaying) {
        setIsBuffering(false);
      } else if (status.isBuffering) {
        setIsBuffering(true);
      } else {
        setIsBuffering(false);
      }

      // 진행률 계산 (드래그 중이 아닐 때만 업데이트)
      if (!isDragging && status.durationMillis && status.durationMillis > 0) {
        const progressValue = status.positionMillis / status.durationMillis;
        setProgress(progressValue);

        // 애니메이션으로 부드럽게 업데이트
        Animated.timing(progressAnim, {
          toValue: progressValue,
          duration: 100,
          useNativeDriver: false,
        }).start();

        // 부모에게 진행률 전달
        onProgressUpdate?.(progressValue, status.durationMillis, isDragging);
      }

      // 비디오 종료 시 progress 초기화
      if (status.didJustFinish) {
        setProgress(0);
        progressAnim.setValue(0);
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

  // Seek 함수를 외부로 노출
  useEffect(() => {
    (window as any).videoSeek = async (seekProgress: number) => {
      if (!videoRef.current || !duration) return;

      try {
        const positionMillis = seekProgress * duration;
        await videoRef.current.setPositionAsync(positionMillis);
        setProgress(seekProgress);
        progressAnim.setValue(seekProgress);
      } catch (error) {
        console.error('Seek error:', error);
      }
    };
  }, [duration]);

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
          {/* Video는 항상 렌더링 - 언마운트 절대 금지 */}
          {!isLoadingSkeleton && (
            <Video
              ref={videoRef}
              source={videoSource}
              style={{ width: SCREEN_WIDTH, height: videoContainerHeight }}
              resizeMode={ResizeMode.CONTAIN}
              isLooping
              shouldPlay={isFocused && !isDragging}
              isMuted={false}
              onPlaybackStatusUpdate={handlePlaybackStatusUpdate}
              progressUpdateIntervalMillis={50}
              useNativeControls={false}
            />
          )}

          {isLoadingSkeleton && (
            <View style={{ width: SCREEN_WIDTH, height: videoContainerHeight, backgroundColor: '#000000' }} />
          )}

          {/* 로딩 인디케이터 - 화면 중앙 */}
          {isBuffering && !isLoadingSkeleton && (
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
    </View>
  );
};
