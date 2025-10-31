/**
 * 사진 갤러리 컴포넌트
 *
 * 인스타그램 스타일의 사진 갤러리
 * - 여러 장의 사진을 좌우 스와이프로 탐색
 * - 하단에 인디케이터 표시 (현재 사진 위치)
 */

import React, { useState, useRef, useCallback } from 'react';
import {
  View,
  Image,
  ScrollView,
  Dimensions,
  NativeSynchedScrollEvent,
  NativeScrollEvent,
  StyleSheet,
} from 'react-native';

const { width: SCREEN_WIDTH, height: SCREEN_HEIGHT } = Dimensions.get('window');

interface PhotoGalleryProps {
  photoUrls: string[];
  width?: number;
  height?: number;
}

export const PhotoGallery: React.FC<PhotoGalleryProps> = ({
  photoUrls,
  width = SCREEN_WIDTH,
  height = SCREEN_HEIGHT,
}) => {
  const [currentIndex, setCurrentIndex] = useState(0);
  const scrollViewRef = useRef<ScrollView>(null);

  // 스크롤 이벤트 처리
  const handleScroll = useCallback(
    (event: NativeSynchedScrollEvent<NativeScrollEvent>) => {
      const contentOffsetX = event.nativeEvent.contentOffset.x;
      const index = Math.round(contentOffsetX / width);
      setCurrentIndex(index);
    },
    [width]
  );

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
      >
        {photoUrls.map((photoUrl, index) => (
          <Image
            key={`${photoUrl}-${index}`}
            source={{ uri: photoUrl }}
            style={[styles.photo, { width, height }]}
            resizeMode="cover"
          />
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
  photo: {
    backgroundColor: '#000',
  },
  indicatorContainer: {
    position: 'absolute',
    top: 16,
    right: 16,
    flexDirection: 'row',
    gap: 6,
    paddingHorizontal: 12,
    paddingVertical: 8,
    backgroundColor: 'rgba(0, 0, 0, 0.5)',
    borderRadius: 16,
  },
  indicator: {
    width: 6,
    height: 6,
    borderRadius: 3,
    backgroundColor: 'rgba(255, 255, 255, 0.5)',
  },
  indicatorActive: {
    backgroundColor: '#FFFFFF',
  },
});
