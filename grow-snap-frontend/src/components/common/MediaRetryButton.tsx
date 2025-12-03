/**
 * 미디어 로딩 실패 시 재시도 버튼 컴포넌트
 * VideoPlayer, PhotoGallery 등에서 공통으로 사용
 */

import React from 'react';
import { View, Text, TouchableWithoutFeedback, StyleSheet } from 'react-native';
import { Ionicons } from '@expo/vector-icons';

interface MediaRetryButtonProps {
  onRetry: () => void;
  message?: string;
}

export const MediaRetryButton: React.FC<MediaRetryButtonProps> = ({
  onRetry,
  message = '미디어를 불러올 수 없습니다',
}) => {
  return (
    <View style={styles.overlay}>
      <TouchableWithoutFeedback onPress={onRetry}>
        <View style={styles.container}>
          <View style={styles.iconContainer}>
            <Ionicons name="refresh" size={40} color="#FFFFFF" />
          </View>
          <Text style={styles.message}>{message}</Text>
          <Text style={styles.subtext}>탭하여 다시 시도</Text>
        </View>
      </TouchableWithoutFeedback>
    </View>
  );
};

const styles = StyleSheet.create({
  overlay: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: 'rgba(0, 0, 0, 0.8)',
  },
  container: {
    alignItems: 'center',
    padding: 24,
  },
  iconContainer: {
    backgroundColor: 'rgba(255, 255, 255, 0.15)',
    borderRadius: 50,
    padding: 20,
    marginBottom: 16,
  },
  message: {
    color: '#FFFFFF',
    fontSize: 16,
    fontWeight: '600',
    marginBottom: 8,
  },
  subtext: {
    color: 'rgba(255, 255, 255, 0.7)',
    fontSize: 14,
    textAlign: 'center',
  },
});
