/**
 * 사진 갤러리 컴포넌트
 *
 * 인스타그램 릴스 스타일의 사진 갤러리
 * - 여러 장의 사진을 좌우 스와이프로 탐색
 * - 하단 중앙에 인디케이터 표시 (현재 사진 위치)
 * - 더블탭으로 좋아요
 */

import React, { useState, useRef, useCallback, useEffect } from 'react';
import {
  View,
  Image,
  ScrollView,
  Dimensions,
  NativeScrollEvent,
  StyleSheet,
  TouchableWithoutFeedback,
} from 'react-native';
import { useTranslation } from 'react-i18next';
import { Ionicons } from '@expo/vector-icons';
import { MediaRetryButton } from '@/components/common';

const { width: SCREEN_WIDTH, height: SCREEN_HEIGHT } = Dimensions.get('window');

interface PhotoGalleryProps {
  photoUrls: string[];
  width?: number;
  height?: number;
  onDoubleTap?: () => void;
  onTap?: () => boolean; // 탭 이벤트, true 반환 시 이벤트 처리됨
}

export const PhotoGallery: React.FC<PhotoGalleryProps> = ({
  photoUrls,
  width = SCREEN_WIDTH,
  height = SCREEN_HEIGHT,
  onDoubleTap,
  onTap,
}) => {
  const { t } = useTranslation('feed');
  const [currentIndex, setCurrentIndex] = useState(0);
  const [imageRetryCount, setImageRetryCount] = useState<Map<number, number>>(new Map());
  const [imageKey, setImageKey] = useState<Map<number, number>>(new Map());
  const [showImageError, setShowImageError] = useState<Set<number>>(new Set());
  const scrollViewRef = useRef<ScrollView>(null);
  const lastTap = useRef<number>(0);
  const retryTimersRef = useRef<Map<number, NodeJS.Timeout>>(new Map());

  const MAX_RETRIES = 3;

  // 컴포넌트 언마운트 시 모든 타이머 정리
  useEffect(() => {
    const timers = retryTimersRef.current;
    return () => {
      timers.forEach(clearTimeout);
      timers.clear();
    };
  }, []);

  // 스크롤 이벤트 처리
  const handleScroll = useCallback(
    (event: { nativeEvent: NativeScrollEvent }) => {
      const contentOffsetX = event.nativeEvent.contentOffset.x;
      const index = Math.round(contentOffsetX / width);
      setCurrentIndex(index);
    },
    [width]
  );

  // 이미지 로드 에러 핸들러 (지수 백오프 재시도)
  const handleImageError = useCallback((index: number) => {
    const currentRetryCount = imageRetryCount.get(index) || 0;

    if (currentRetryCount < MAX_RETRIES) {
      // 자동 재시도
      const delay = Math.min(1000 * 2 ** currentRetryCount, 30000);

      const timerId = setTimeout(() => {
        setImageRetryCount((prev) => {
          const newMap = new Map(prev);
          newMap.set(index, currentRetryCount + 1);
          return newMap;
        });
        setImageKey((prev) => {
          const newMap = new Map(prev);
          newMap.set(index, (prev.get(index) || 0) + 1);
          return newMap;
        });
        retryTimersRef.current.delete(index);
      }, delay);
      retryTimersRef.current.set(index, timerId);
    } else {
      // 모든 재시도 실패 시에만 에러 로그
      console.error(`[PhotoGallery] Image load failed after ${MAX_RETRIES} retries at index ${index}`);
      setShowImageError((prev) => new Set(prev).add(index));
    }
  }, [imageRetryCount, MAX_RETRIES]);

  // 수동 재시도 핸들러
  const handleManualRetry = useCallback((index: number) => {
    setImageRetryCount((prev) => {
      const newMap = new Map(prev);
      newMap.set(index, 0);
      return newMap;
    });
    setShowImageError((prev) => {
      const newSet = new Set(prev);
      newSet.delete(index);
      return newSet;
    });
    setImageKey((prev) => {
      const newMap = new Map(prev);
      newMap.set(index, (prev.get(index) || 0) + 1);
      return newMap;
    });
  }, []);

  // 더블탭 핸들러
  const handlePress = useCallback(() => {
    const now = Date.now();
    const DOUBLE_TAP_DELAY = 300;

    if (now - lastTap.current < DOUBLE_TAP_DELAY) {
      // 더블탭
      onDoubleTap?.();
      lastTap.current = 0;
    } else {
      // 싱글탭
      lastTap.current = now;
      setTimeout(() => {
        if (lastTap.current === now) {
          onTap?.();
        }
      }, DOUBLE_TAP_DELAY);
    }
  }, [onDoubleTap, onTap]);

  return (
    <View style={[styles.container, { width, height }]}>
      {/* 사진 스크롤뷰 */}
      <ScrollView
        ref={scrollViewRef}
        horizontal
        pagingEnabled
        showsHorizontalScrollIndicator={false}
        onScroll={handleScroll}
        scrollEventThrottle={16}
        style={styles.scrollView}
        contentContainerStyle={styles.scrollViewContent}
      >
        {photoUrls.map((photoUrl, index) => {
          const hasError = showImageError.has(index);
          const key = imageKey.get(index) || 0;

          return (
            <TouchableWithoutFeedback key={`${photoUrl}-${index}-${key}`} onPress={handlePress}>
              <View style={[styles.photoContainer, { width, height }]}>
                {!hasError ? (
                  <Image
                    key={key}
                    source={{ uri: photoUrl }}
                    style={styles.photo}
                    resizeMode="contain"
                    onError={() => handleImageError(index)}
                  />
                ) : (
                  /* 재시도 버튼 - 모든 재시도 실패 시 표시 */
                  <MediaRetryButton
                    onRetry={() => handleManualRetry(index)}
                    message={t('media.imageLoadError')}
                  />
                )}
              </View>
            </TouchableWithoutFeedback>
          );
        })}
      </ScrollView>

      {/* 인디케이터 (사진이 2장 이상일 때만 표시) */}
      {photoUrls.length > 1 && (
        <View style={styles.indicatorContainer}>
          {photoUrls.map((_, index) => (
            <View
              key={index}
              style={[
                styles.indicator,
                index === currentIndex && styles.indicatorActive,
              ]}
            />
          ))}
        </View>
      )}
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    position: 'relative',
    backgroundColor: '#000',
  },
  scrollView: {
    flex: 1,
  },
  scrollViewContent: {
    alignItems: 'center',
  },
  photoContainer: {
    backgroundColor: '#000',
    justifyContent: 'center',
    alignItems: 'center',
  },
  photo: {
    width: '100%',
    height: '100%',
  },
  indicatorContainer: {
    position: 'absolute',
    bottom: 80,
    left: 0,
    right: 0,
    flexDirection: 'row',
    justifyContent: 'center',
    alignItems: 'center',
    gap: 6,
  },
  indicator: {
    width: 6,
    height: 6,
    borderRadius: 3,
    backgroundColor: 'rgba(255, 255, 255, 0.4)',
  },
  indicatorActive: {
    backgroundColor: '#FFFFFF',
    width: 7,
    height: 7,
  },
});
