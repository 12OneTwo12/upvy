/**
 * 사진 갤러리 컴포넌트
 *
 * 인스타그램 릴스 스타일의 사진 갤러리
 * - 여러 장의 사진을 좌우 스와이프로 탐색
 * - 하단 중앙에 인디케이터 표시 (현재 사진 위치)
 * - 더블탭으로 좋아요
 */

import React, { useState, useRef, useCallback } from 'react';
import {
  View,
  Image,
  ScrollView,
  Dimensions,
  NativeScrollEvent,
  StyleSheet,
  TouchableWithoutFeedback,
} from 'react-native';

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
  const [currentIndex, setCurrentIndex] = useState(0);
  const scrollViewRef = useRef<ScrollView>(null);
  const lastTap = useRef<number>(0);

  // 스크롤 이벤트 처리
  const handleScroll = useCallback(
    (event: { nativeEvent: NativeScrollEvent }) => {
      const contentOffsetX = event.nativeEvent.contentOffset.x;
      const index = Math.round(contentOffsetX / width);
      setCurrentIndex(index);
    },
    [width]
  );

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
        {photoUrls.map((photoUrl, index) => (
          <TouchableWithoutFeedback key={`${photoUrl}-${index}`} onPress={handlePress}>
            <View style={[styles.photoContainer, { width, height }]}>
              <Image
                source={{ uri: photoUrl }}
                style={styles.photo}
                resizeMode="contain"
              />
            </View>
          </TouchableWithoutFeedback>
        ))}
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
